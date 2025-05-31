(ns kit.kit-test.web.ui.layout
  (:require
    [kit.kit-test.web.htmx :as htmx]))

(defn full-page [_req & children]
  (htmx/page {}
    [:div.mt-12.p-12.max-w-lg.mx-auto.rounded-2xl
     {:class "border border-carrara"}
     [:div
      [:h1.heading "HTMX Test App"]
      [:div {:id "app-root"}
       children]]]))
