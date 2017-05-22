(ns clack.slack
  (:require [aleph.http :as http-ws]
            [clojure.data.json :as json]
            [manifold.stream :as ms]
            [org.httpkit.client :as http]))

(def SLACK_API_URL "https://slack.com/api")

(defn- parse-initial-config
  [body]
  (let [data (json/read-str body)]
    {:my-user-id (get-in data ["self" "id"])
     :websocket-url (data "url")}))

(defn- parse-message
  [msg]
  (json/read-str msg))

(defn- setup-keepalive [conn running]
  (future
    (while @running (do
      (Thread/sleep 10000)
      (try
        (do (println "Keepalive thread. closed?" (ms/closed? conn))
            (ms/put! conn "{\"type\": \"ping\"}"))
        (catch Exception e
          (println "Exception on keepalive thread" e)))
    (println "Exiting keepalive thread")))))

(defn get-initial-config
  [slack-api-token]
  (future
    (let [opts {:query-params {:token slack-api-token}}
          url (str SLACK_API_URL "/rtm.connect")
          {:keys [status body error]} @(http/get url opts)]
      (if error
        (throw (Exception. "Error" error))
        (parse-initial-config body)))))

(defn setup-stream-consumer
  [websocket-url my-user-id running]
  (try
    (let [conn @(http-ws/websocket-client websocket-url)
          keepalive (setup-keepalive conn running)]
      (ms/consume #(println "GOT NEW MSG" %) conn)
      (ms/on-closed conn #(do
        (println "CLOSED STREAM")
        (reset! running false))))
    (catch Exception e
      (reset! running false))))

(declare reset-watcher)

(defn run-forever-2
  [websocket-url]
  (let [running (atom true)]
    (add-watch running websocket-url reset-watcher)
    (setup-stream-consumer websocket-url "user-id" running)))

(defn run-forever
  [slack-api-token]
  (println "Initializing with api key" slack-api-token)
  (let [running (atom true)]
    (add-watch running slack-api-token reset-watcher)
    (try
      (let [config @(get-initial-config slack-api-token)
            {:keys [websocket-url my-user-id]} config]
        (setup-stream-consumer websocket-url my-user-id running))
      (catch Exception e
        (reset! running false)))))

(defn reset-watcher
  [key running old-state new-state]
  (println "Connection reseted. Sleeping before reconnecting...")
  (Thread/sleep 5000)
  (run-forever key))
