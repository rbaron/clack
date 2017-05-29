(ns clack.clack-test
  (:require [clojure.test :refer :all]
            [clack.clack :refer :all]
            [clojure.data.json :as json]
            [aleph.http :as http-ws]
            [manifold.stream :as ms]
            [org.httpkit.client :as http]))

(defn make-mock
  [impl]
  (let [call-count (atom 0)
        call-args (atom [])]
    (let [get-fn (fn [& args]
                   (swap! call-count inc)
                   (swap! call-args #(conj % args))
                   (impl))
          get-call-cound #(call-count)
          dispatch (fn [msg]
            (cond (= msg 'get-fn) get-fn
                  (= msg 'get-call-count) @call-count
                  (= msg 'get-call-args) @call-args))]
      dispatch)))

(deftest get-initial-config-test
  (letfn [
    (http-get-initial-config-mock-success-impl
      [& args]
      (future
        {:body (json/write-str {"self" {"id" "my-user-id"}
                                "url" "ws://localhost:10000"})}))

    (http-get-initial-config-mock-error-impl
      [& args]
      (future
        {:error 401}))]

    (testing "Get initial config success"
      (let [mock-get-initial-config (make-mock http-get-initial-config-mock-success-impl)]
        (with-redefs [http/get (mock-get-initial-config 'get-fn)]
          (let [res @(get-initial-config "some-token")
                call-count (mock-get-initial-config 'get-call-count)
                call-args (mock-get-initial-config 'get-call-args)]
            (is (= res {:websocket-url "ws://localhost:10000" :my-user-id "my-user-id"}))
            (is (= call-count 1))))))

    (testing "Get initial config error"
      (let [mock-get-initial-config (make-mock http-get-initial-config-mock-error-impl)]
        (with-redefs [http/get (mock-get-initial-config 'get-fn)]
          (let [res @(get-initial-config "some-token")]
            (is (nil? res))))))))

(deftest run-forever-test
  (testing "Calls setup-channels-and-wait on success"
    (letfn [
      (get-initial-config-mock-impl
        [& args]
        (future
        {:websocket-url "ws://localhost:10000" :my-user-id "my-user-id"}))

      (connect-mock-impl
        [& args]
        "connect-return-value")

      (setup-channels-and-wait-mock-impl
        [& args]
        "setup-channels-and-wait-return-value")

      (retry-mock-impl
        [& args]
        "retry-return-value")

      (handler
        [in out]
        "handler-return-value")]

    (let [mock-get-initial-config (make-mock get-initial-config-mock-impl)
          mock-connect (make-mock connect-mock-impl)
          mock-retry (make-mock retry-mock-impl)
          mock-setup-channels-and-wait (make-mock setup-channels-and-wait-mock-impl)]
      (with-redefs [get-initial-config (mock-get-initial-config 'get-fn)
                    connect (mock-connect 'get-fn)
                    retry (mock-retry 'get-fn)
                    setup-channels-and-wait (mock-setup-channels-and-wait 'get-fn)]
        (let [res (run-forever "slack-api-token" "handler" {:keepalive-interval "ka-int"})]

          #_(is (= (res) "retry-return-value"))
          #_(is (= (mock-setup-channels-and-wait 'get-call-count) 1))
          (is (= (first (mock-setup-channels-and-wait 'get-call-args))
                 ["connect-return-value" "handler" @(get-initial-config-mock-impl) "ka-int"])))))))

  (testing "Returns a retry function if get-initial-config fails"
    (letfn [
      (get-initial-config-mock-impl
        [& args]
        (future nil))

      (connect-mock-impl
        [& args]
        "connect-return-value")

      (setup-channels-and-wait-mock-impl
        [& args]
        "setup-channels-and-wait-return-value")

      (retry-mock-impl
        [& args]
        "retry-return-value")

      (handler
        [in out]
        "handler-return-value")]

    (let [mock-get-initial-config (make-mock get-initial-config-mock-impl)
          mock-connect (make-mock connect-mock-impl)
          mock-retry (make-mock retry-mock-impl)
          mock-setup-channels-and-wait (make-mock setup-channels-and-wait-mock-impl)]
      (with-redefs [get-initial-config (mock-get-initial-config 'get-fn)
                    connect (mock-connect 'get-fn)
                    retry (mock-retry 'get-fn)
                    setup-channels-and-wait (mock-setup-channels-and-wait 'get-fn)]
        (let [res (run-forever "slack-api-token" "handler" {})]

          (is (= (res) "retry-return-value"))
          (is (= (mock-setup-channels-and-wait 'get-call-count) 0)))))))

  (testing "Returns a retry function if connect fails"
    (letfn [
      (get-initial-config-mock-impl
        [& args]
        (future {:websocket-url "ws://localhost:10000" :my-user-id "my-user-id"}))

      (connect-mock-impl
        [& args]
        nil)

      (setup-channels-and-wait-mock-impl
        [& args]
        "setup-channels-and-wait-return-value")

      (retry-mock-impl
        [& args]
        "retry-return-value")

      (handler
        [in out]
        "handler-return-value")]

    (let [mock-get-initial-config (make-mock get-initial-config-mock-impl)
          mock-connect (make-mock connect-mock-impl)
          mock-retry (make-mock retry-mock-impl)
          mock-setup-channels-and-wait (make-mock setup-channels-and-wait-mock-impl)]
      (with-redefs [get-initial-config (mock-get-initial-config 'get-fn)
                    connect (mock-connect 'get-fn)
                    retry (mock-retry 'get-fn)
                    setup-channels-and-wait (mock-setup-channels-and-wait 'get-fn)]
        (let [res (run-forever "slack-api-token" "handler" {})]

          (is (= (res) "retry-return-value"))
          (is (= (mock-setup-channels-and-wait 'get-call-count) 0))))))))

(deftest setup-channels-and-wait-test
  (testing "Unblocks thread when stream is closed"
    (letfn [(handler [in-chan out-chan config] nil)]
        (let [conn (ms/stream)]
          (ms/close! conn)
          (is (nil? (setup-channels-and-wait conn handler {} 10000)))))))
