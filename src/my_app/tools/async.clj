(ns my-app.tools.async
  (:require
    [clojure.core.async :as a]))

(defn pipe-with!
  "Starts a loop that listens to values put on the channel, and calls 'f' with
  them. Stops when the channel closes."
  [ch f]
  (a/go-loop []
    (when-let [x (a/<! ch)]
      (f x)
      (recur))))

(comment
  (def ch (a/chan))
  (pipe-with! ch (fn [x] (println "Got " x)))
  (a/put! ch "Hello")
  (a/put! ch "Some other value")
  (a/close! ch))
