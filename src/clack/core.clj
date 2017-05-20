(ns clack.core
  (:require [environ.core :refer [env]]
            [clack.phabricator :as phab])
  (:gen-class))

(def config {
  :phabricator-api-url (env :phabricator-api-url)
  :phabricator-api-token (env :phabricator-api-token)
})

(defn -main
  [& args]
  (let [rev @(phab/get-revision (:phabricator-api-url config)
                                (:phabricator-api-token config)
                                10000)
        user @(phab/get-user (:phabricator-api-url config)
                             (:phabricator-api-token config)
                             (:author-id rev))]
    (println "Got rev" rev user)
    (shutdown-agents)))
