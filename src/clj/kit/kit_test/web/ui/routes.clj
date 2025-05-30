(ns kit.kit-test.web.ui.routes
  (:require
    [integrant.core :as ig]
    [kit.kit-test.web.middleware.exception :as exception]
    [kit.kit-test.web.middleware.formats :as formats]
    [kit.kit-test.web.ui.hello :as ui.hello]
    [reitit.ring.middleware.muuntaja :as muuntaja]
    [reitit.ring.middleware.parameters :as parameters]))

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
      :or   {base-path ""}
      :as   opts}]
  [base-path route-data (ui.hello/ui-routes (str base-path "/ui/"))])
