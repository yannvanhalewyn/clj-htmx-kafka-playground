(ns my-app.web.async-render
  (:require
    [clojure.core.async :as a]
    [clojure.tools.logging :as log]
    [hiccup2.core :as h]
    [my-app.tools.async :as async]
    [my-app.web.sse :as sse])
  (:import
    [java.util.concurrent CancellationException]))

(defn suspense [req {:keys [placeholder key sse-session]} delayed-content]
  (future
    (try
      (let [html (str (h/html @delayed-content))]
        (sse/send! req
          (sse/new-message sse-session key html)))
      (sse/unregister-cleanup! req sse-session [::suspense key])

      ;; Cancellation exceptions are expected
      (catch CancellationException _e)
      (catch Exception e
        (log/error e "Error in delayed render"))))

  (sse/register-cleanup! req sse-session
    [::suspense key]
    #(future-cancel delayed-content))

  [:div {:sse-swap key}
   placeholder])

(defn stream
  "Can stream updates to a component based on markup put on a channel.
  Provide a 'chime-fn', which takes a channel and puts hiccup to it, zero or
  more times. Every hiccup message will be sent to the client via SSE. Once
  done, calling the done-fn."
  [req {:keys [key sse-session] :as params} chime-fn]
  (let [hiccup-ch (a/chan)
        done-fn (fn []
                 (a/close! hiccup-ch)
                 (sse/unregister-cleanup! req sse-session [::stream key]))]

    ;; Register the cleanup for if the client disconnects
    (sse/register-cleanup! req sse-session
      [::stream key]
      done-fn)

    ;; Start the caller's routine
    (chime-fn hiccup-ch done-fn)

    ;; Send received hiccup via SSE
    (async/pipe-with! hiccup-ch
      #(sse/send! req
         (sse/new-message sse-session key
           (str (h/html %)))))

    ;; Return an HTMX component
    [:div (assoc (select-keys params [:hx-swap])
            :sse-swap key)]))
