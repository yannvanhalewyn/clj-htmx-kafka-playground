(ns my-app.web.ui.layout
  (:require
    [hiccup2.core :as h]
    [my-app.tools.ui :as ui]
    [my-app.web.htmx :as htmx]
    [ring.util.http-response :as ring.response]))

(defn- header []
  [:header.flex.items-center.justify-between
   (ui/link "/" {:class "brand flex items-center gap-3"}
     (list
       [:span.logo "</>"]
       [:span
        [:span.block.text-xl.font-semibold.text-white "HTMX Test App"]
        [:span.block.text-sm.text-lavender
         "Hypermedia experiments in Clojure"]]))
   [:a.text-sm.text-lavender
    {:href "https://htmx.org/docs/" :target "_blank" :rel "noopener"
     :class "whitespace-nowrap hover:text-white"}
    "htmx docs ↗"]])

(defn- wire-log
  "Filled in by /js/app.js, which listens to HTMX events on the document."
  []
  [:aside.card.p-4.self-start {:class "lg:sticky lg:top-8"}
   [:div.flex.items-center.justify-between
    [:h2.font-semibold "📡 Wire log"]
    [:button.text-xs.text-lavender.cursor-pointer
     {:id "wire-log-clear" :type "button" :class "hover:text-white"}
     "clear"]]
   [:p.mt-1.text-xs.text-lavender
    "Every request HTMX makes, and where the response went."]
   [:ol.mt-3.space-y-2.max-h-96.overflow-y-auto {:id "wire-log"}]
   [:p.mt-3.text-xs.text-lavender {:id "wire-log-empty"}
    "Nothing yet. Poke a demo!"]])

(defn full-page [_req & children]
  (htmx/page {:title "HTMX Test App"}
    [:div.mx-auto.max-w-6xl.px-6.py-10
     (header)
     [:div.mt-10.grid.gap-8 {:class "lg:grid-cols-[minmax(0,1fr)_20rem]"}
      [:main {:id "app-root"}
       children]
      (wire-log)]]))

(defn demo
  "The shell around every demo page: a title, what you're looking at, the live
  demo itself, how it works and the code that powers it.

  `info` is a map with :emoji :title :intro :attrs :steps and :code, the latter
  being a list of vars to show the source of."
  [{:keys [emoji title intro attrs steps code]} & children]
  (list
    (ui/link "/" {:class "text-sm text-lavender hover:text-white"}
      "← All demos")
    [:div.mt-4.flex.items-center.gap-3
     [:span.text-4xl emoji]
     [:h1.heading-1 title]]
    [:p.mt-3.prose-intro intro]
    (when (seq attrs)
      [:div.mt-4.flex.flex-wrap.gap-2
       (for [attr attrs]
         (ui/attr-chip attr))])
    [:section.try-it
     [:span.try-it__label "Try it"]
     children]
    (when (seq steps)
      [:section.mt-10
       [:h2.heading-2 "How it works"]
       [:ol.steps.mt-4
        (for [step steps]
          [:li step])]])
    (when (seq code)
      (apply ui/show-code code))))

(defn if-page-load [req & children]
  (->
    (if (htmx/hx-request? req)
      (str (h/html children))
      (full-page req children))
    (ring.response/ok)
    #_{:clj-kondo/ignore [:unresolved-var]}
    (ring.response/content-type "text/html")))
