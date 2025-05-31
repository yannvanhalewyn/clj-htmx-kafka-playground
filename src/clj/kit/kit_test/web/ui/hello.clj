(ns kit.kit-test.web.ui.hello
  (:require
    [kit.kit-test.web.htmx :as htmx]
    [simpleui.core :as simpleui :refer [defcomponent]]))

(defcomponent ^:endpoint hello [_req my-name]
  [:div#hello
   (when my-name
     [:span.italic.ml-2 "Hello: " my-name])])

(defcomponent ^:endpoint cart [_req cart-msg]
  [:div#cart
   (when cart-msg
     [:span.italic.ml-2 "Cart: " cart-msg])])

(defn ui-routes [base-path]
  (simpleui/make-routes
    base-path
    (fn [req]
      (htmx/page {}
        [:div.mt-12.p-12.max-w-lg.mx-auto.rounded-2xl
         {:class "border border-carrara"}
         [:h1.heading "HTMX Test App"]
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

         [:div.mt-4
          [:label.block.font-semibold.mr-4 "Message"]
          [:textarea.input.mt-1.w-full
           {:name "cart-msg"
            :rows 3
            :placeholder "Insert message"
            :hx-patch "cart"
            :hx-trigger "input changed delay:100ms"
            :hx-target "#cart"
            :hx-swap "outerHTML"}]]
         [:div.mt-4
          (cart req nil)]]))))

(comment
  (ui-routes "/ui/"))
