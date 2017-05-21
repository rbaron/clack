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

(defn- setup-keepalive [conn]
  (let [callback (fn [] (ms/put! conn "{\"type\": \"ping\"}"))]
    (future (while true (do
      (Thread/sleep 10000)
      (callback))))))

(defn get-initial-config
  [slack-api-token]
  (future
    (let [opts {:query-params {:token slack-api-token}}
          url (str SLACK_API_URL "/rtm.connect")
          {:keys [status body error]} @(http/get url opts)]
      (parse-initial-config body))))

(defn run-forever
  [websocket-url my-user-id]
  (let [conn @(http-ws/websocket-client websocket-url)
        msg (atom true)]
    #_(while (not (nil? msg))
      (let [new-msg @(ms/take! conn ::drained)]
        (reset! msg new-msg)
        (println "GOT MSG" new-msg (ms/closed? conn))))

    #_(println "exiting...")
    (setup-keepalive conn)
    (ms/consume #(println "GOT NEW MSG" %) conn)
    (ms/on-closed conn #(println "CLOSED STREAM"))
    ))
