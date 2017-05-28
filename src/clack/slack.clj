(ns clack.slack
  (:require [aleph.http :as http-ws]
            [clojure.data.json :as json]
            [clojure.core.async :as async]
            [manifold.stream :as ms]
            [org.httpkit.client :as http]))

(def SLACK_API_URL "https://slack.com/api")

(defn- parse-initial-config
  [body]
  (let [data (json/read-str body)]
    {:my-user-id (get-in data ["self" "id"])
     :websocket-url (data "url")}))

(defn get-initial-config
  [slack-api-token]
  (future
    (let [opts {:query-params {:token slack-api-token}}
          url (str SLACK_API_URL "/rtm.connect")
          {:keys [status body error]} @(http/get url opts)]
      (if error
        nil
        (parse-initial-config body)))))

(defn- async-keepalive-loop
  [conn msg-chan]
  (async/go-loop []
    (let [[v ch] (async/alts!! [msg-chan (async/timeout 10000)])]
      (if (not= v :kill-self)
        (do (ms/put! conn "{\"type\": \"ping\"}")
            (recur))
        (println "Keepalive thread is exiting...")))))

(defn- async-output-loop
  [conn out-msg-chan]
  (async/go-loop []
    (if-let [msg (async/<! out-msg-chan)]
      (do (ms/put! conn (json/write-str msg))
          (recur))
      (println "Exiting async output loop"))))

(defn connect
  [websocket-url]
  (try
    @(http-ws/websocket-client websocket-url)
    (catch java.net.ConnectException e
      nil)))

(defn- make-consumer
  [in-msg-chan]
  (fn [msg]
    (let [json-msg (json/read-str msg :keyword-fn keyword)]
      (async/go (async/>! in-msg-chan json-msg)))))

(defn setup-channels-and-wait
  [conn handler]
  (let [keepalive-msg-chan (async/chan)
        block-chan (async/chan)
        in-msg-chan (async/chan)
        out-msg-chan (async/chan)]
    (do (async-keepalive-loop conn keepalive-msg-chan)
        (async-output-loop conn out-msg-chan)
        (handler in-msg-chan out-msg-chan)
        (ms/consume (make-consumer in-msg-chan) conn)
        (ms/on-closed conn #(do (println "CLOSED STREAM")
                                (async/>!! keepalive-msg-chan :kill-self)
                                (async/close! in-msg-chan)
                                (async/close! out-msg-chan)
                                (async/close! block-chan)
                                (async/close! keepalive-msg-chan)))
        (async/<!! block-chan))))

(declare run-forever)

(defn retry
  [slack-api-token handler options message timeout]
  (println message "Retrying in" timeout "ms...")
  (Thread/sleep timeout)
  #(run-forever slack-api-token handler options))

(defn run-forever
  [slack-api-token handler options]
  (try
    (if-let [config @(get-initial-config slack-api-token)]
      (let [{:keys [websocket-url my-user-id]} config]
        (println "Got initial config" config)
        (if-let [conn (connect websocket-url)]
          (do (setup-channels-and-wait conn handler)
              #(retry slack-api-token handler options "Connection closed." 0))
          #(retry slack-api-token handler options "Could not connect to websocket." 10000)))
      #(retry slack-api-token handler options "Could not fetch initial config." 10000))
    (catch java.io.IOException e
      #(retry slack-api-token handler options (str "Error: " e) 10000))))

(defn start
  [slack-api-token handler options]
  (trampoline run-forever slack-api-token handler options))
