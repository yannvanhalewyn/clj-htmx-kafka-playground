(ns kit.kit-test.web.ui.hello
  (:require
    [kit.kit-test.web.htmx :as htmx]
    [simpleui.core :as su :refer [defcomponent]]))

(defcomponent ^:endpoint hello [_req my-name]
  [:div#hello
   (when my-name
     [:span.italic.ml-2 "Hello: " my-name])])

(defcomponent ^:endpoint cart [_req cart-msg]
  [:div#cart
   (when cart-msg
     [:span.italic.ml-2 "Cart: " cart-msg])])

(defn hidden [name val]
  [:input.input
   {:type "hidden"
    :id name
    :name name
    :value val}])

(defn text [name val]
  [:input.input
   {:type "text"
    :id name
    :name name
    :value val}])

(defn email [name val]
  [:input.input
   {:type "email"
    :id name
    :name name
    :value val}])

(defcomponent ^:endpoint form-edit [req first-name last-name email*]
  [:form.space-y-2 {:hx-put "show-user"}
   [:div
    [:label.font-semibold.mr-2 "First Name"]
    (text "first-name" first-name)]
   [:div
    [:label.font-semibold.mr-2 "Last Name"]
    (text "last-name" last-name)]
   [:div
    [:label.font-semibold.mr-2 "Email Address"]
    (email "email" email*)]
   [:div.mt-4
    [:button.btn.mr-2 "Save"]
    [:button.btn {:hx-get "show-user"}
     "Cancel"]]])

(defcomponent ^:endpoint show-user [req first-name last-name email]
  ;; make sure form-edit is included in endpoints
  form-edit
  [:form.space-y-2
   {:hx-target "this"}
   [:div.space-y-2
    (hidden "first-name" first-name)
    [:div [:label.font-semibold "First Name"] ": " first-name]
    (hidden "last-name" last-name)
    [:div [:label.font-semibold "Last Name"] ": " last-name]
    (hidden "email" email)
    [:div [:label.font-semibold "Email"] ": " email]
    [:button.text-link.margin
     {:hx-put "form-edit"}
     "Edit"]]])

(defn ui-routes [base-path]
  (su/make-routes
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
            :hx-swap "outerHTML"}]
          [:div.mt-4
           (cart req nil)]]

         [:hr.border-carrara]
         [:div.mt-4
          (show-user req "Joe" "Blow" "joe@blow.com")]]))))
(comment
  (ui-routes "/ui/"))
