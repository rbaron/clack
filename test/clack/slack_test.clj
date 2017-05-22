(ns clack.slack-test
  (:require [clojure.test :refer :all]
            [clack.slack :refer :all]
            [clojure.data.json :as json]
            [aleph.http :as http-ws]
            [manifold.stream :as ms]
            [org.httpkit.client :as http]))

(def non-websocket-request
  {:status 400
   :headers {"content-type" "application/text"}
   :body "Expected a websocket request."})

(def conn (atom nil))

(defn echo-handler
  [req]
  (if-let [socket (try
                    @(http-ws/websocket-connection req)
                    (catch Exception e
                      nil))]
    (ms/connect socket socket)
    non-websocket-request))

;(def s (http-ws/start-server echo-handler {:port 10000}))

;(deftest websocket-disconnect-test
;  (testing "Works"
;    (let [s (http-ws/start-server echo-handler {:port 10000})
;          running (atom true)]
;      (add-watch running nil reset-watcher)
;      (setup-stream-consumer "ws://localhost:10000" "my-user-id" running)
;      (ms/put! @conn "{}")
;      (ms/close! @conn)
;      (.close s)
;      (reset! conn nil)
;      (let [s2 (http-ws/start-server echo-handler {:port 10000})
;            running2 (atom true)]
;        (setup-stream-consumer "ws://localhost:10000" "my-user-id" running2)
;        (ms/put! @conn "{2: 1}")
;        (ms/close! @conn)
;        (.close s2)
;        (reset! conn nil)))))

(defn mock-initial-config
  [slack-api-token]
  (future
    {:websocket-url "ws://localhost:10000"
     :my-user-id "my-user-id"}))

(defn mock-initial-config-get
  [url opts]
  (future
    {:body (json/write-str {"self" {"id" "my-user-id"}
                            "url" "ws://localhost:10000"})}))

(deftest run-forever-test
  (testing "Run forever"
    (with-redefs [http/get mock-initial-config-get]
      (let [s (http-ws/start-server echo-handler {:shutdown-executor? false :port 10000})]
        (run-forever "slack-api-token")
        (.close s)))))
