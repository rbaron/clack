(ns clack.core
  (:require [environ.core :refer [env]]
            [clack.phabricator :as phab]
            [clack.slack :as slack])
  (:gen-class))

(def config {
  :phabricator-api-url (env :phabricator-api-url)
  :phabricator-api-token (env :phabricator-api-token)
  :slack-api-token (env :slack-api-token)
})

(defn -main
  [& args]

  #_(let [rev @(phab/get-revision (:phabricator-api-url config)
                                (:phabricator-api-token config)
                                10000)
        user @(phab/get-user (:phabricator-api-url config)
                             (:phabricator-api-token config)
                             (:author-id rev))]
    (println "Got rev" rev user))

  #_(let [res @(slack/get-initial-config (:slack-api-token config))]
    (slack/setup-stream-consumer (:websocket-url res)
                                 (:my-user-id res)))

  #_(slack/run-forever-2 "ws://localhost:8080")
  (slack/run-forever (:slack-api-token config))

    #_(shutdown-agents))
