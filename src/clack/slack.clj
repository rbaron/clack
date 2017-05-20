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

(defn get-initial-config
  [slack-api-token]
  (future
    (let [opts {:query-params {:token slack-api-token}}
          url (str SLACK_API_URL "/rtm.connect")
          {:keys [status body error]} @(http/get url opts)]
      (parse-initial-config body))))

(defn run-forever
  [websocket-url my-user-id]
  (let [conn @(http-ws/websocket-client websocket-url)]
    (while true
      (let [msg @(ms/take! conn)]
        (println "GOT MSG" msg)))))
