(ns kit.kit-test.web.htmx
  (:require
    [hiccup.page :as p]
    [ring.util.http-response :as ring.response]))

(defn page [{:keys [title]} & body]
  (->
    (ring.response/ok
      (p/html5 {:lang "en"}
        [:head
         [:meta {:charset "UTF-8"}]
         [:title title]
         [:script {:src "https://unpkg.com/htmx.org@2.0.4/dist/htmx.min.js" :defer true}]]
        [:body
         body]))
    #_{:clj-kondo/ignore [:unresolved-var]}
    (ring.response/content-type "text/html")))
