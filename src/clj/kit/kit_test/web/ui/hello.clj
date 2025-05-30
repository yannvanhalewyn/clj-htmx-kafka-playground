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
        [:label {:style "margin-right: 10px"}
         "What is your name?"]
        [:input {:type "text"
                 :name "my-name"
                 :hx-patch "hello"
                 :hx-trigger "input changed delay:200ms"
                 :hx-target "#hello"
                 :hx-swap "outerHTML"}]
        (hello req "")
        [:input {:type "text"
                 :name "cart-msg"
                 :hx-patch "cart"
                 :hx-trigger "input changed delay:200ms"
                 :hx-target "#cart"
                 :hx-swap "outerHTML"}]
        (cart req "")))))

(comment
  (ui-routes "/ui/"))
