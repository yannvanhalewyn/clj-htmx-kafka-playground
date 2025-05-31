(ns kit.kit-test.web.ui.routes
  (:require
    [integrant.core :as ig]
    [kit.kit-test.tools.ui :as ui]
    [kit.kit-test.web.middleware.exception :as exception]
    [kit.kit-test.web.middleware.formats :as formats]
    [kit.kit-test.web.ui.hx-playground :as hx-playground]
    [kit.kit-test.web.ui.layout :as layout]
    [kit.kit-test.web.ui.person-history :as person-history]
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
      (layout/full-page req
        [:div.mt-4.space-y-3
         [:div
          (ui/link "person-history" "Person History")]
         [:div
          (ui/link "hx-playground" "HTMX Playground")]]))))

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
