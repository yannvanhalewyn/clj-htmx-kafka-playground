(ns my-app.tools.response
  (:require
    [clojure.core.async :as a]
    [clojure.java.io :as io]
    [ring.core.protocols :as protocols])
  (:import
    (java.io Closeable)))

(def ^:const EOL "\n")

(defn- as-streamable-response-body
  [output-ch {:keys [cleanup]}]
  (reify protocols/StreamableResponseBody
    (write-body-to-stream [_ _res output-stream]
      (let [writer (io/writer output-stream)]
        (try
          (loop []
            (let [heartbeat-ch (a/timeout 10000)
                  [msg from] (a/alts!! [output-ch heartbeat-ch])]
              (doto writer
                (.write (if (= from heartbeat-ch) ^String EOL ^String msg))
                (.flush))
              (recur)))
          ;; Exit from the loop on exception.
          (catch Exception _)
          (finally
            (when cleanup (cleanup))
            (.close ^Closeable writer)
            (.close ^Closeable output-stream)))))))

(defn event-stream-response
  "Returns streaming ring response for source async chan.
  The optional :cleanup is a (fn []) to invoke on cleanup."
  {:arglists '([output-chan]
               [output-chan, {:keys [cleanup]}])}
  ([output-ch]
   (event-stream-response output-ch nil))
  ([output-ch opts]
   {:status 200
    :headers {"Content-Type" "text/event-stream;charset=UTF-8"
              "Cache-Control" "no-store"}
    :body (as-streamable-response-body output-ch opts)}))

