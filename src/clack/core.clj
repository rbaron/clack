(ns clack.core
  (:require [environ.core :refer [env]]
            [clojure.core.async :as async]
            [clack.clack :as slack])
  (:gen-class))

(def config {
  :slack-api-token (env :slack-api-token)
})

(defn handler
  [in-msg-chan out-msg-chan]
  (async/go-loop
    []
    ; While channel is open
    (if-let [msg (async/<! in-msg-chan)]
      (do
        (println "Got new message" msg)
        ;(async/>! out-msg-chan {:type :message})
        (recur))
      (println "Exiting main handler"))))

(defn -main
  [& args]
  (slack/start (:slack-api-token config) handler {}))
