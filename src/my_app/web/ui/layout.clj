(ns my-app.web.ui.layout
  (:require
    [hiccup2.core :as h]
    [my-app.web.htmx :as htmx]
    [ring.util.http-response :as ring.response]))

(defn full-page [_req & children]
  (htmx/page {:title "HTMX Test App"}
    [:div.mt-12.p-12.max-w-lg.mx-auto.rounded-2xl
     {:class "border border-carrara"}
     [:div
      [:h1.heading-1 "HTMX Test App"]
      [:div {:id "app-root"}
       children]]]))

(defn if-page-load [req & children]
  (->
    (if (htmx/hx-request? req)
      (str (h/html children))
      (full-page req children))
    (ring.response/ok)
    #_{:clj-kondo/ignore [:unresolved-var]}
    (ring.response/content-type "text/html")))
