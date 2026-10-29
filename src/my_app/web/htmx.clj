(ns my-app.web.htmx
  (:require
    [hiccup.page :as hp]
    [hiccup2.core :as h]))

(defn hx-request? [req]
  (some-> (:headers req) (contains? "hx-request")))

(defn page
  "Renders a full html page. Uses hiccup2 so strings are escaped, just like
  in the partials HTMX swaps in."
  [{:keys [title]} & body]
  (str
    (h/html {:mode :html}
      (h/raw "<!DOCTYPE html>\n")
      [:html {:lang "en"}
       [:head
        [:meta {:charset "UTF-8"}]
        [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
        [:title title]
        (hp/include-css
          "/styles.css"
          "https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.11.1/styles/atom-one-dark.min.css")

        (hp/include-js
          "https://unpkg.com/hyperscript.org@0.9.12"
          "https://unpkg.com/htmx.org@2.0.4/dist/htmx.min.js"
          "https://unpkg.com/htmx-ext-sse@2.2.3"
          "https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.11.1/highlight.min.js"
          "https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.11.1/languages/clojure.min.js"
          "/js/app.js")]

       [:body.bg-firefly
        body]])))

(comment
  (page {:title "My Page"}
    [:h1 "Welcome to my page"]))
