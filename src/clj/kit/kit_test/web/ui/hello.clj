(ns kit.kit-test.web.ui.hello
  (:require
   [kit.kit-test.tools.request :as request]
   [kit.kit-test.tools.ui :as ui]
   [kit.kit-test.web.htmx :as htmx]
   [simpleui.core :as su :refer [defcomponent]]
   [xtdb.api :as xt]))

(defcomponent ^:endpoint hello [_req my-name]
  [:div#hello
   (when my-name
     [:span.italic.ml-2 "Hello: " my-name])])

(defcomponent ^:endpoint cart [_req cart-msg]
  [:div#cart
   (when cart-msg
     [:span.italic.ml-2 "Cart: " cart-msg])])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Person Form

(defn find-person [db]
  (xt/entity db :person))

(defn upsert-person! [db-node person]
  (xt/submit-tx db-node
    [[::xt/put (assoc person :xt/id :person)]]))

(comment
  (find-person (user/db))
  (upsert-person! (user/db-node)
    {:person/first-name "Tom"
     :person/last-name "Riddle"
     :person/email "voldymort@hogwards.edu"}))

(defcomponent ^:endpoint form-edit [req first-name last-name email*]
  [:form.space-y-2
   {:hx-patch (str 'show-user)}
   [:div
    [:label.font-semibold.mr-2 "First Name"]
    (ui/text "first-name" first-name)]
   [:div
    [:label.font-semibold.mr-2 "Last Name"]
    (ui/text "last-name" last-name)]
   [:div
    [:label.font-semibold.mr-2 "Email Address"]
    (ui/email "email" email*)]
   [:div.mt-4
    [:button.btn.mr-2 "Save"]
    [:button.btn {:hx-get "show-user"}
     "Cancel"]]])

(defcomponent ^:endpoint show-user [req first-name last-name email]
  ;; make sure form-edit is included in endpoints
  form-edit
  (when (request/patch? req)
    (upsert-person! (:db-node req)
      {:person/first-name first-name
       :person/last-name last-name
       :person/email email}))
  [:form.space-y-2
   {:hx-target "this"}
   [:div.space-y-2
    (ui/hidden "first-name" first-name)
    [:div [:label.font-semibold "First Name"] ": " first-name]
    (ui/hidden "last-name" last-name)
    [:div [:label.font-semibold "Last Name"] ": " last-name]
    (ui/hidden "email" email)
    [:div [:label.font-semibold "Email"] ": " email]
    [:button.text-link.margin
     {:hx-get (str 'form-edit)}
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
          (let [{:person/keys [first-name last-name email]}
                (or (find-person (xt/db (:db-node req)))
                    {:person/first-name "John"
                     :person/last-name "Doe"
                     :person/email "john.doe@example.com"})]
            (show-user req first-name last-name email))]]))))

(comment
  (ui-routes "/ui/"))
