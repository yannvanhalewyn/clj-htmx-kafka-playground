(ns kit.kit-test.web.htmx
  (:require
    [hiccup.page :as p]))

(defn hx-request? [req]
  (some-> (:headers req) (contains? "hx-request")))

(defn page [{:keys [title]} & body]
  (p/html5 {:lang "en"}
    [:head
     [:meta {:charset "UTF-8"}]
     [:title title]
     (p/include-css "/styles.css")

     [:script
      {:src "https://unpkg.com/htmx.org@2.0.4/dist/htmx.min.js"
       :type "text/javascript",
       :defer true
       :crossorigin "anonymous"}]
     [:script
      {:src "https://unpkg.com/htmx-ext-sse@2.2.3"
       :type "text/javascript",
       :defer true
       :crossorigin "anonymous"}]]
    [:body.bg-firefly
     body]))

(comment
  (page {:title "My Page"}
    [:h1 "Welcome to my page"]))
