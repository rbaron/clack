(ns clack.phabricator
  (:require [clojure.data.json :as json]
            [org.httpkit.client :as http]))

(defn- parse-revision
  [body]
  (let [data (json/read-str body)
        res (get-in data ["result" "data" 0 "fields"])]
    {:title (res "title")
     :author-id (res "authorPHID")
     :created-at (res "dateCreated")
     :updated-at (res "dateModified")}))

(defn- parse-user
  [user-id body]
  (let [data (json/read-str body)
        res (get-in data ["result" user-id])]
    {:id (res "phid")
     :name (res "name")
     :full-name (res "fullName")}))

(defn get-revision
  [api-url api-token rev-id]
  (future
    (let [opts {:insecure? true
                :query-params {"api.token" api-token
                               "constraints[ids][0]" rev-id}}
          url (str api-url "/differential.revision.search")
          {:keys [status body error]} @(http/get url opts)]
      (parse-revision body))))

(defn get-user
  [api-url api-token user-id]
  (future
    (let [opts {:insecure? true
                :query-params {"api.token" api-token
                               "names[0]" user-id}}
          url (str api-url "/phid.lookup")
          {:keys [status body error]} @(http/get url opts)]
      (parse-user user-id body))))
