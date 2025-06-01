(ns my-app.web.ui.routes
  (:require
    [integrant.core :as ig]
    [my-app.tools.ui :as ui]
    [my-app.web.middleware.exception :as exception]
    [my-app.web.middleware.formats :as formats]
    [my-app.web.ui.chat :as chat]
    [my-app.web.ui.delayed-render-sse :as delayed-render-sse]
    [my-app.web.ui.hx-playground :as hx-playground]
    [my-app.web.ui.layout :as layout]
    [my-app.web.ui.person-history :as person-history]
    [reitit.ring.middleware.muuntaja :as muuntaja]
    [reitit.ring.middleware.parameters :as parameters]
    [simpleui.core :as su]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Person Form

(defn ui-routes [base-path]
  (su/make-routes
    base-path
    #_{:clj-kondo/ignore [:unused-value]}
    (fn [req]
      person-history/person-history
      hx-playground/hx-playground
      delayed-render-sse/delayed-render
      chat/chat
      (layout/if-page-load req
        [:div.mt-4.space-y-3
         [:div
          (ui/link "person-history" "Person History")]
         [:div
          (ui/link "hx-playground" "HTMX Playground")]
         [:div
          (ui/link "delayed-render" "Delayed Render")]
         [:div
          (ui/link "chat" "Chat")]]))))

(comment
  (ui-routes ""))

(def route-data
  {:muuntaja   formats/instance
   :middleware
   [;; Default middleware for ui
    ;; query-params & form-params
    parameters/parameters-middleware
    ;; encoding response body
    muuntaja/format-response-middleware
    ;; exception handling
    exception/wrap-exception]})

(defmethod ig/init-key ::routes
  [_ {:keys [base-path]
      :or   {base-path ""}}]
  [base-path route-data (ui-routes base-path)])
