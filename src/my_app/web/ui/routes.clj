(ns my-app.web.ui.routes
  (:require
    [integrant.core :as ig]
    [my-app.tools.ui :as ui]
    [my-app.web.middleware.exception :as exception]
    [my-app.web.middleware.formats :as formats]
    [my-app.web.ui.chat :as chat]
    [my-app.web.ui.delayed-render-sse :as delayed-render-sse]
    [my-app.web.ui.hx-playground :as hx-playground]
    [my-app.web.ui.layout :as layout]
    [my-app.web.ui.person-history :as person-history]
    [reitit.ring.middleware.muuntaja :as muuntaja]
    [reitit.ring.middleware.parameters :as parameters]
    [simpleui.core :as su]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Home

(def demos
  [[hx-playground/info "#FFC857"]
   [person-history/info "#FF6FAE"]
   [delayed-render-sse/info "#5EEAD4"]
   [chat/info "#A78BFA"]])

(defn- demo-tile [{:keys [path emoji title blurb attrs]} accent]
  (ui/link path {:class "demo-tile" :style (str "--accent: " accent)}
    (list
      [:span.demo-tile__emoji emoji]
      [:h2.mt-3.text-lg.font-semibold.text-white title]
      [:p.mt-1.text-sm.text-lavender blurb]
      [:div.mt-4.flex.flex-wrap.gap-1.5
       (for [attr attrs]
         (ui/attr-chip attr :static? true))])))

(defn- home []
  (list
    [:h1.text-4xl.font-semibold.leading-tight
     "Hypermedia, " [:span.text-sun "but make it fun"] " ✨"]
    [:p.mt-4.prose-intro.max-w-2xl
     "Little experiments with " [:a.text-link {:href "https://htmx.org"} "htmx"]
     ", server-sent events and " [:a.text-link {:href "https://github.com/whamtet/simpleui"} "SimpleUI"]
     ". The server renders HTML, and a few attributes decide what to request
     and where to put the answer. Each demo explains itself, and has a "
     [:strong "Show code"] " button with the exact source that runs it."]
    [:div.mt-10.grid.gap-4 {:class "sm:grid-cols-2"}
     (for [[info accent] demos]
       (demo-tile info accent))]
    [:p.mt-8.text-sm.text-lavender
     "👉 Keep an eye on the wire log. It shows every request as it happens."]))

(defn ui-routes [base-path]
  (su/make-routes
    base-path
    #_{:clj-kondo/ignore [:unused-value]}
    (fn [req]
      person-history/person-history
      hx-playground/hx-playground
      delayed-render-sse/delayed-render
      chat/chat
      (layout/if-page-load req
        (home)))))

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
