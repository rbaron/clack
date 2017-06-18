(defproject clack "0.1.0"
  :description "Simple bot framework for Slack"
  :url "https://github.com/clack"
  :license {:name "MIT"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/core.async "0.3.442"]
                 [org.clojure/data.json "0.2.6"]
                 [aleph "0.4.3"]
                 [com.taoensso/timbre "4.10.0"]
                 [environ "1.1.0"]
                 [http-kit "2.2.0"]]
  :main ^:skip-aot clack.example
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
