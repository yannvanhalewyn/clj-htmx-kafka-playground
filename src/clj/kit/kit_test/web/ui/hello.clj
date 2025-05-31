(ns kit.kit-test.web.ui.hello
  (:require
    [kit.kit-test.web.htmx :as htmx]
    [simpleui.core :as simpleui :refer [defcomponent]]))

(defcomponent ^:endpoint hello [_req my-name]
  [:div#hello "Hello " my-name])

(defcomponent ^:endpoint cart [_req cart-msg]
  [:div#cart "Cart: " cart-msg])

(defn ui-routes [base-path]
  (simpleui/make-routes
    base-path
    (fn [req]
      (htmx/page {}
        [:div.p-12
         [:div
          [:label.mr-4 "Name"]
          [:input
           {:type "text"
            :name "my-name"
            :placeholder "Insert name"
            :hx-patch "hello"
            :hx-trigger "input changed delay:200ms"
            :hx-target "#hello"
            :hx-swap "outerHTML"}]
          (hello req "")]

         [:div
          [:label.mr-4 "Message"]
          [:input
           {:type "text"
            :name "cart-msg"
            :placeholder "Insert message"
            :hx-patch "cart"
            :hx-trigger "input changed delay:200ms"
            :hx-target "#cart"
            :hx-swap "outerHTML"}]]
         (cart req "")]))))

(comment
  (ui-routes "/ui/"))
