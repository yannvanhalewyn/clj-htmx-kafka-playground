(ns my-app.web.htmx
  (:require
    [hiccup.page :as hp]))

(defn hx-request? [req]
  (some-> (:headers req) (contains? "hx-request")))

(defn page [{:keys [title]} & body]
  (hp/html5 {:lang "en"}
    [:head
     [:meta {:charset "UTF-8"}]
     [:title title]
     (hp/include-css "/styles.css")

     (hp/include-js
       "https://unpkg.com/hyperscript.org@0.9.12"
       "https://unpkg.com/htmx.org@2.0.4/dist/htmx.min.js"
       "https://unpkg.com/htmx-ext-sse@2.2.3")]

    [:body.bg-firefly
     body]))

(comment
  (page {:title "My Page"}
    [:h1 "Welcome to my page"]))
