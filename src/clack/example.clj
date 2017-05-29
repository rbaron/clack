(ns clack.example
  (:require [clack.clack :as clack]
            [clojure.core.async :as async]
            [environ.core :refer [env]])
  (:gen-class))

(clack/set-log-level! :info)

(defn send-ack
  [msg out-chan my-user-id]
  (if (and (= (:type msg) "message")
           (not= (:user my-user-id) my-user-id))
    (async/>! out-chan {:type "message"
                        :channel (:channel msg)
                        :text "Ok!"})))

(defn handler
  [in-chan out-chan config]
  (let [{:keys [my-user-id websocket-url]} config]
    (async/go-loop
      []
      (if-let [msg (async/<! in-chan)]
        (do
          (send-ack msg out-chan my-user-id)
          (recur))
        (println "Channel is closed")))))

(defn -main
  [& args]
  (clack/start (env :slack-api-token) handler))
