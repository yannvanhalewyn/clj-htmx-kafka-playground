(ns kit.kit-test.web.ui.person-history
  (:require
    [clojure.string :as str]
    [kit.kit-test.tools.date :as date]
    [kit.kit-test.tools.request :as request]
    [kit.kit-test.tools.ui :as ui]
    [simpleui.core :as su]
    [xtdb.api :as xt]
    [kit.kit-test.web.ui.layout :as layout]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; DB

(defn find-person [db]
  (xt/entity db :person))

(defn upsert-person! [db-node person]
  (xt/submit-tx db-node
    [[::xt/put (assoc person :xt/id :person)]]))

(defn full-name [person]
  (str (:person/first-name person) " " (:person/last-name person)))

(defn search-key [person]
  (str/lower-case
    (str (full-name person) " "
         (:person/email person))))

(comment
  (find-person (user/db))
  (full-name (find-person (user/db)))
  (upsert-person! (user/db-node)
    {:person/first-name "Tom"
     :person/last-name "Riddle"
     :person/email "voldymort@hogwards.edu"})
  (search-key
    {:person/first-name "John"
     :person/last-name "Doe"
     :person/email "john.doe@example.com"}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; UI

(su/defcomponent ^:endpoint form-edit [_req first-name last-name email*]
  [:form.space-y-2
   {:hx-patch (str 'show-user)
    :hx-target "#app-root"}
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

(su/defcomponent ^:endpoint history-items [req q]
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

(su/defcomponent ^:endpoint show-user [req first-name last-name email]
  ;; make sure form-edit is included in endpoints
  form-edit
  history-items
  (let [person {:person/first-name first-name
                :person/last-name last-name
                :person/email email}]

    (when (request/patch? req)
      (upsert-person! (:db-node req) person))

    (layout/if-page-load req
      (ui/link "/" "← Back")
      [:form.space-y-2
       {:hx-target "this"}
       [:div.mt-4
        (ui/hidden "first-name" first-name)
        (ui/hidden "last-name" last-name)
        (ui/hidden "email" email)
        [:div
         [:label.block.font-semibold.text-lavender (full-name person)]
         [:span.italic email]]
        [:button.mt-1.text-link.margin
         {:hx-get (str 'form-edit)}
         "Edit"]]]

      [:hr.mt-4.border-carrara]

      [:div.mt-6
       [:div.flex.items-center.justify-between
        [:h2.heading-2.text-md "Entity History"]
        [:input.input.input--dark
         {:type "text"
          :id "q"
          :name "q"
          :placeholder "Search..."
          :hx-trigger "input changed delay:100ms"
          :hx-get (str 'history-items)
          :hx-target "#history-items"}]]

       [:div.mt-6
        {:id "history-items"}
        (history-items req nil)]])))

(su/defcomponent ^:endpoint person-history [req]
  show-user
  (let [{:person/keys [first-name last-name email]}
        (or (find-person (xt/db (:db-node req)))
            {:person/first-name "John"
             :person/last-name "Doe"
             :person/email "john.doe@example.com"})]
    (show-user req first-name last-name email)))
