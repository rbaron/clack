(ns clack.example
  (:require [clack.clack :as clack]
            [clojure.core.async :as async]
            [environ.core :refer [env]])
  (:gen-class))

(defn send-ack
  [msg out-chan my-user-id]
  (if (and (= (:type msg) "message")
           (not= (:user my-user-id) my-user-id))
    (async/go (async/>! out-chan {:type "message"
                                  :channel (:channel msg)
                                  :text "Ok!"}))))

(defn handler
  [in-chan out-chan config]
  (async/go-loop []
    (if-let [msg (async/<! in-chan)]
      (do
        (println "Got msg" msg)
        (send-ack msg out-chan (:my-user-id config))
        (recur))
      (println "Channel is closed"))))

(defn -main
  [& args]
  (clack/start (env :slack-api-token) handler))
