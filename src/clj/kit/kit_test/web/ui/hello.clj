(ns kit.kit-test.web.ui.hello
  (:require
   [clojure.string :as str]
   [kit.kit-test.tools.date :as date]
   [kit.kit-test.tools.request :as request]
   [kit.kit-test.tools.ui :as ui]
   [kit.kit-test.web.htmx :as htmx]
   [simpleui.core :as su :refer [defcomponent]]
   [xtdb.api :as xt]
   [clojure.core :as c]))

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

(defn full-name [person]
  (str (:person/first-name person) " " (:person/last-name person)))

(comment
  (find-person (user/db))
  (upsert-person! (user/db-node)
    {:person/first-name "Tom"
     :person/last-name "Riddle"
     :person/email "voldymort@hogwards.edu"}))

(defcomponent ^:endpoint form-edit [_req first-name last-name email*]
  [:form.space-y-2
   {:hx-patch (str 'show-user)
    :hx-target "#user-section"}
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

(defn search-key [person]
  (str/lower-case
   (str (full-name person) " "
        (:person/email person))))

(comment
  (search-key
    {:person/first-name "John"
     :person/last-name "Doe"
     :person/email "john.doe@example.com"}))

(defcomponent ^:endpoint history-items [req q]
  (let [q (some-> q str/trim str/lower-case)
        entity-history
        (cond->>
          (xt/entity-history (xt/db (:db-node req)) :person :desc
            {:with-docs? true})
          q (filter #(str/includes? (search-key (:xtdb.api/doc %)) q)))]
    [:ul.mt-2.space-y-4
     (for [{:keys [xtdb.api/doc xtdb.api/tx-time]} entity-history]
       [:li
        [:div
         [:label.block.font-semibold (full-name doc)]
         [:span.italic (:person/email doc)]]
        [:p.italic "Edited at " (date/format-date tx-time)]])]))

(defcomponent ^:endpoint show-user [req first-name last-name email]
  ;; make sure form-edit is included in endpoints
  form-edit
  history-items
  (let [person
        {:person/first-name first-name
         :person/last-name last-name
         :person/email email}]
    (when (request/patch? req)
      (upsert-person! (:db-node req) person))
    [:div
     {:id "user-section"}
     [:form.space-y-2
      {:hx-target "this"}
      [:div
       (ui/hidden "first-name" first-name)
       (ui/hidden "last-name" last-name)
       (ui/hidden "email" email)
       [:div
        [:label.block.font-semibold.text-lavender (full-name person)]
        [:span.italic email]]
       [:button.mt-1.text-link.margin
        {:hx-get (str 'form-edit)}
        "Edit"]]]

     [:div.mt-4
      [:div.flex.items-center.justify-between
       [:h2.heading "Entity History"]
       [:input.input
        {:type "text"
         :id "q"
         :name "q"
         :placeholder "Search..."
         :hx-trigger "input changed delay:100ms"
         :hx-get (str 'history-items)
         :hx-target "#history-items"}]]

      [:div
       {:id "history-items"}
       (history-items req nil)]]]))

(defn ui-routes [base-path]
  (su/make-routes
    base-path
    (fn [req]
      (htmx/page {}
        [:div.mt-12.p-12.max-w-lg.mx-auto.rounded-2xl
         {:class "border border-carrara"}
         [:div
          [:h1.heading "HTMX Test App"]]

         ;; Greeter
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
           (cart req nil)]]

         [:hr.border-carrara]

         ;; Person and entity history
         [:div.mt-4
          (let [{:person/keys [first-name last-name email]}
                (or (find-person (xt/db (:db-node req)))
                    {:person/first-name "John"
                     :person/last-name "Doe"
                     :person/email "john.doe@example.com"})]
            (show-user req first-name last-name email))]]))))

(comment
  (ui-routes "/ui/"))
