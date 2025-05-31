(ns kit.kit-test.web.ui.hx-playground
  (:require
    [simpleui.core :as su]))

(su/defcomponent ^:endpoint hello [_req my-name]
  [:div#hello
   (when my-name
     [:span.italic.ml-2 "Hello: " my-name])])

(su/defcomponent ^:endpoint cart [_req cart-msg]
  [:div#cart
   (when cart-msg
     [:span.italic.ml-2 "Cart: " cart-msg])])

(su/defcomponent ^:endpoint hx-playground [req]
  hello
  cart
  ;; Greeter
  (list
    [:div.mt-4
     [:label.block.font-semibold.mr-4 "Name"]
     [:input.input.mt-1.w-full
      {:type "text"
       :name "my-name"
       :placeholder "Insert name"
       :hx-patch "hello"
       :hx-trigger "input changed delay:100ms"
       :hx-target "#hello"
       :hx-swap "outerHTML"}]
     [:div.mt-4
      (hello req nil)]]

  ;; Message echo
    [:div.mt-4
     [:label.block.font-semibold.mr-4 "Message"]
     [:textarea.input.mt-1.w-full
      {:name "cart-msg"
       :rows 3
       :placeholder "Insert message"
       :hx-patch "cart"
       :hx-trigger "input changed delay:100ms"
       :hx-target "#cart"
       :hx-swap "outerHTML"}]
     [:div.mt-4
      (cart req nil)]]))

