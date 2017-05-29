(defproject clack "0.1.0"
  :description "Simple Phabricator bot for Slack"
  :url "https://rbaron.net/slackie"
  :license {:name "MIT"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/core.async "0.3.442"]
                 [org.clojure/data.json "0.2.6"]
                 [aleph "0.4.3"]
                 [environ "1.1.0"]
                 [http-kit "2.2.0"]]
  :main ^:skip-aot clack.example
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
