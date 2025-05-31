(ns kit.kit-test.web.ui.delayed-render-sse
  (:require
    [clojure.tools.logging :as log]
    [hiccup2.core :as h]
    [kit.kit-test.web.sse :as sse]
    [kit.kit-test.web.ui.layout :as layout]
    [simpleui.core :as su]))

(def SSE_TOPIC_NAME "delayed-message-test")
(def SSE_EVENT "update-body")

(su/defcomponent ^:endpoint delayed-render [req]
  (sc.api/spy :endpoint)
  (comment
   (sc.api/letsc 42 (keys req)))
  (do
    (future
      (try
       (Thread/sleep 3000)
       (sse/send! req
         {:sse/event SSE_EVENT
          :sse/data (str (h/html [:p "Loaded async!"]))
          :sse/topic SSE_TOPIC_NAME})
       (catch Exception e
         (log/error e "------ Error in delayed render"))))
    (layout/if-page-load req
      (sse/hx-listener
        {:sse/topic SSE_TOPIC_NAME
         :sse/event SSE_EVENT}
        [:p "Some Message"]))))

(comment
  (defn replace-body [msg]
    (sse/send! (user/sse-listener)
      {:sse/event SSE_EVENT
       :sse/data (str (h/html [:p msg]))
       :sse/topic SSE_TOPIC_NAME}))
  (replace-body "Hello!")
  (replace-body "Replace async"))
