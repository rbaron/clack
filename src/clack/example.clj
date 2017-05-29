(ns clack.example
  (:require [clack.clack :as clack]
            [clojure.core.async :as async]
            [environ.core :refer [env]])
  (:gen-class))

(defn handler
  [in-chan out-chan]
  (async/go-loop
    []
    ; While channel is open
    (if-let [msg (async/<! in-chan)]
      (do
        (println "Got new message" msg)
        (recur))
      (println "Exiting main handler"))))

(defn -main
  [& args]
  (clack/start (env :slack-api-token) handler {:debug true}))
