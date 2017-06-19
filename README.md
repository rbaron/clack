# clack

A minimalistic Slack bot framework. It automatically handles (re-)connection and keepalive messages for you, so you can focus on the fun part!

# Installation

Add to your `project.clj`:
```clojure
[clack "0.1.0"]
```

# Usage

The only entrypoint is the function `clack/start`, which runs forever. It receives two parameters: your Slack API token and a `handler` function. The `handler` function, which you should implement, should be a function of three parameters:

1. `in-chan` - a `core.async` channel from which to take messages
2. `out-chan` - a `core.async` channel to which to post messages
3. `config` - a clojure map with (so far) keys `[:my-user-id]`

Check out the example below for a complete description.

# Full example

This example shows the implementation of a simple bot that always replies `"Ok!"` to every message received on a channel -- except the ones it sends itself (otherwise we'd create an infinite loop). It pulls your Slack API token from the env var `SLACK_API_TOKEN`.

```clojure
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
```

# License

MIT.
