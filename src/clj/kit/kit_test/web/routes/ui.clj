(ns kit.kit-test.web.routes.ui
  (:require
   [kit.kit-test.web.middleware.exception :as exception]
   [kit.kit-test.web.middleware.formats :as formats]
   [kit.kit-test.web.routes.utils :as utils]
   [kit.kit-test.web.htmx :refer [page pagelet] :as htmx]
   [integrant.core :as ig]
   [reitit.ring.middleware.muuntaja :as muuntaja]
   [reitit.ring.middleware.parameters :as parameters]))

(defn home [request]
  (page {:lang "en"}
   [:head
    [:meta {:charset "UTF-8"}]
    [:title "Htmx + Kit"]
    [:script {:src "https://unpkg.com/htmx.org@2.0.4/dist/htmx.min.js" :defer true}]]
   [:body
    [:h1 "Welcome to Htmx + Kit module"]
    [:button {:hx-post "/clicked" :hx-swap "outerHTML"} "Click me!"]]))

(defn clicked [request]
  (pagelet
   [:div "Congratulations! You just clicked the button!"]))

;; Routes
(defn ui-routes [_opts]
  [["/" {:get home}]
   ["/clicked" {:post clicked}]])

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

(derive :reitit.routes/ui :reitit/routes)

(defmethod ig/init-key :reitit.routes/ui
  [_ {:keys [base-path]
      :or   {base-path ""}
      :as   opts}]
  (fn [] [base-path route-data (ui-routes opts)]))
