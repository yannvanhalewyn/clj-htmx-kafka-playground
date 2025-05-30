(ns kit.kit-test.web.ui.routes
  (:require
    [integrant.core :as ig]
    [kit.kit-test.web.htmx :refer [page pagelet] :as htmx]
    [kit.kit-test.web.middleware.exception :as exception]
    [kit.kit-test.web.middleware.formats :as formats]
    [kit.kit-test.web.ui.hello :as ui.hello]
    [reitit.ring.middleware.muuntaja :as muuntaja]
    [reitit.ring.middleware.parameters :as parameters]))

(defn home [_req]
  (page {:lang "en"}
    [:head
     [:meta {:charset "UTF-8"}]
     [:title "Htmx + Kit"]
     [:script {:src "https://unpkg.com/htmx.org@2.0.4/dist/htmx.min.js" :defer true}]]
    [:body
     [:h1 "Welcome to Htmx + Kit module"]
     [:button {:hx-post "/clicked" :hx-swap "outerHTML"} "Click me!"]]))

(defn clicked [_req]
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

(defmethod ig/init-key ::routes
  [_ {:keys [base-path]
      :or   {base-path ""}
      :as   opts}]
  [base-path route-data (ui.hello/ui-routes (str base-path "/ui/"))])
