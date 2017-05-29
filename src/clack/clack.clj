(ns clack.clack
  (:require [aleph.http :as http-ws]
            [clojure.data.json :as json]
            [clojure.core.async :as async]
            [manifold.stream :as ms]
            [org.httpkit.client :as http]
            [taoensso.timbre :as timbre]))

(def SLACK_API_URL "https://slack.com/api")

(defn- parse-initial-config
  [body]
  (timbre/debug "Got initial config: " body)
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
  [conn msg-chan interval]
  (async/go-loop []
    (let [[v ch] (async/alts!! [msg-chan (async/timeout interval)])]
      (if (not= v :kill-self)
        (do (ms/put! conn (json/write-str {:type "ping"}))
            (recur))
        (timbre/debug "Keepalive thread is exiting...")))))

(defn- async-output-loop
  [conn out-msg-chan]
  (async/go-loop []
    (if-let [msg (async/<! out-msg-chan)]
      (do (ms/put! conn (json/write-str msg))
          (recur))
      (timbre/debug "Exiting async output loop"))))

(defn connect
  [websocket-url]
  (try
    @(http-ws/websocket-client websocket-url)
    (catch java.net.ConnectException e
      nil)))

(defn- make-consumer
  [in-msg-chan]
  (fn [msg]
    (let [json-msg (json/read-str msg :key-fn keyword)]
      (async/go (async/>! in-msg-chan json-msg)))))

(defn setup-channels-and-wait
  [conn handler config keepalive-interval]
  (let [keepalive-msg-chan (async/chan)
        block-chan (async/chan)
        in-msg-chan (async/chan)
        out-msg-chan (async/chan)]
    (do (async-keepalive-loop conn keepalive-msg-chan keepalive-interval)
        (async-output-loop conn out-msg-chan)
        (handler in-msg-chan out-msg-chan config)
        (ms/consume (make-consumer in-msg-chan) conn)
        (ms/on-closed conn #(do (timbre/debug "Closed stream")
                                (async/>!! keepalive-msg-chan :kill-self)
                                (async/close! in-msg-chan)
                                (async/close! out-msg-chan)
                                (async/close! block-chan)
                                (async/close! keepalive-msg-chan)))
        (async/<!! block-chan))))

(declare run-forever)

(defn retry
  [slack-api-token handler options message timeout]
  (timbre/info message "Retrying in" timeout "ms...")
  (Thread/sleep timeout)
  #(run-forever slack-api-token handler options))

(def default-options
  {:keepalive-interval 10000
   :retry-interval 10000})

(defn run-forever
  [slack-api-token handler options]
  (let [{:keys [keepalive-interval retry-interval]} options
        retry-fn (fn [msg interval] #(retry slack-api-token handler options msg interval))]
    (try
      (if-let [config @(get-initial-config slack-api-token)]
        (let [{:keys [websocket-url my-user-id]} config]
          (timbre/info "Got initial config")
          (if-let [conn (connect websocket-url)]
            (do (timbre/info "Connected to websocket")
                (setup-channels-and-wait conn handler config keepalive-interval)
                (retry-fn "Connection closed." 0))
            (retry-fn "Could not connect to websocket." retry-interval)))
        (retry-fn "Could not fetch initial config." retry-interval))
      (catch java.io.IOException e
        (retry-fn (str "Error: " e) retry-interval)))))

(defn start
  [slack-api-token handler & [{:as options}]]
  (trampoline run-forever slack-api-token handler (merge default-options options)))

(defn set-log-level!
  "`level` is one of `#{:trace :debug :info :warn :error :fatal :report}`"
  [level]
  (timbre/set-level! level))
