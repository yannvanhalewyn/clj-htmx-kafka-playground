(ns kit.kit-test.web.ui.delayed-render-sse
  (:require
    [hiccup2.core :as h]
    [kit.kit-test.web.async-render :as async-render]
    [kit.kit-test.web.sse :as sse]
    [kit.kit-test.web.ui.layout :as layout]
    [simpleui.core :as su]))

(def SSE_TOPIC_NAME "delayed-message-test")

(su/defcomponent ^:endpoint delayed-render [req]
  (layout/if-page-load req
    (sse/hx-listener
      {:sse/topic SSE_TOPIC_NAME}
      (async-render/suspense req
        {:placeholder [:p "First Suspense"]
         :key "delayed-body"
         :sse/topic SSE_TOPIC_NAME}
        (future (Thread/sleep 3000)
          [:p "Loaded async!"])))))

(comment
  (defn replace-body [msg]
    (sse/send! (user/sse-listener)
      {:sse/event "delayed-body"
       :sse/data (str (h/html [:p msg]))
       :sse/topic SSE_TOPIC_NAME}))
  (replace-body "Hello!")
  (replace-body "Replace async"))
