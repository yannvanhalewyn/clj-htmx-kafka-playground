(ns kit.kit-test.web.async-render
  (:require
    [clojure.core.async :as a]
    [clojure.tools.logging :as log]
    [hiccup2.core :as h]
    [kit.kit-test.web.sse :as sse]))

(defn suspense [req {:keys [placeholder key sse-session]} delayed-content]
  (future
    (try
      (let [html (str (h/html @delayed-content))]
        (sse/send! req
          (sse/new-message sse-session key html)))
      (catch Exception e
        (log/error e "Error in delayed render"))))
  [:div {:sse-swap key}
   placeholder])

(defn stream
  "Can stream updates to a component based on markup put on a channel.
  Provide a 'chime-fn', which takes a channel and puts hiccup to it, zero or
  more times. Every hiccup message will be sent to the client via SSE. Once
  done, calling the done-fn."
  [req {:keys [key sse-session] :as params} chime-fn]
  (let [hiccup-ch (a/chan)]
    (chime-fn hiccup-ch #(a/close! hiccup-ch))
    (a/go-loop []
      (when-let [hiccup (a/<! hiccup-ch)]
        (sse/send! req
          (sse/new-message sse-session key
            (str (h/html hiccup))))
        (recur)))
    [:div (assoc (select-keys params [:hx-swap])
            :sse-swap key)]))
