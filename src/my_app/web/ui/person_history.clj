(ns my-app.web.ui.person-history
  (:require
    [clojure.string :as str]
    [my-app.tools.date :as date]
    [my-app.tools.request :as request]
    [my-app.tools.ui :as ui]
    [my-app.web.ui.layout :as layout]
    [simpleui.core :as su]
    [xtdb.api :as xt]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; DB

(defn find-person [db]
  (xt/entity db :person))

(defn upsert-person! [db-node person]
  (xt/submit-tx db-node
    [[::xt/put (assoc person :xt/id :person)]]))

(defn full-name [person]
  (str (:person/first-name person) " " (:person/last-name person)))

(defn initials [person]
  (->> [(:person/first-name person) (:person/last-name person)]
    (keep #(some-> % str/trim not-empty first))
    (apply str)
    (str/upper-case)))

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

(def info
  {:path "person-history"
   :emoji "🕰️"
   :title "Person History"
   :blurb "Edit a person, then travel back through every version of them."
   :intro "XTDB keeps every version of a document. Edit the person below,
           then search through their history. All with a handful of HTMX
           attributes and server rendered HTML."
   :attrs ["hx-get" "hx-patch" "hx-target" "hx-include" "hx-trigger"]
   :steps
   [(list "Clicking " [:strong "Edit"] " sends a " (ui/code "GET form-edit")
          " and swaps the card for a form, thanks to "
          (ui/code "hx-target=\"this\"") " on the card. "
          (ui/code "hx-include") " sends the hidden fields along.")
    (list "Saving submits the form as " (ui/code "PATCH show-user")
          ". The server writes a new version to XTDB and re-renders the whole
          demo into " (ui/code "#app-root") ".")
    (list "The search box sends " (ui/code "GET history-items?q=…")
          " as you type and only swaps the list below it.")]})

(su/defcomponent ^:endpoint form-edit [_req first-name last-name email*]
  [:form.space-y-4
   {:hx-patch (str 'show-user)
    :hx-target "#app-root"}
   [:div.grid.gap-4 {:class "sm:grid-cols-2"}
    [:div
     [:label.label {:for "first-name"} "First name"]
     (ui/text "first-name" first-name)]
    [:div
     [:label.label {:for "last-name"} "Last name"]
     (ui/text "last-name" last-name)]]
   [:div
    [:label.label {:for "email"} "Email address"]
    (ui/email "email" email*)]
   [:div.flex.gap-2
    [:button.btn.btn-primary "💾 Save"]
    [:button.btn {:type "button" :hx-get "person-history"}
     "Cancel"]]])

(su/defcomponent ^:endpoint history-items [req q]
  (let [q (some-> q str/trim str/lower-case)
        entity-history
        (cond->>
          (xt/entity-history (xt/db (:db-node req)) :person :desc
            {:with-docs? true})
          q (filter #(str/includes? (search-key (:xtdb.api/doc %)) q)))]
    (if (empty? entity-history)
      [:p.text-lavender "No versions match " [:em q] " 🔍"]
      [:ol.timeline
       (for [{:keys [xtdb.api/doc xtdb.api/tx-time]} entity-history]
         [:li
          [:span.block.font-semibold (full-name doc)]
          [:span.block.text-sm.text-lavender (:person/email doc)]
          [:span.block.text-xs.text-lavender.opacity-60
           "Edited " (date/format-date tx-time)]])])))

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
      (layout/demo
        (assoc info :code [#'show-user #'form-edit #'history-items])
        [:div.rounded-xl.p-4 {:hx-target "this" :class "bg-white/5"}
         [:div.flex.items-center.gap-4
          (ui/hidden "first-name" first-name)
          (ui/hidden "last-name" last-name)
          (ui/hidden "email" email)
          [:span.avatar (initials person)]
          [:div.min-w-0.flex-1
           [:span.block.text-lg.font-semibold (full-name person)]
           [:span.block.truncate.text-lavender email]]
          [:button.btn
           {:hx-get (str 'form-edit)
            :hx-include "closest div"}
           "✏️ Edit"]]]

        [:div.mt-8
         [:div.flex.items-center.justify-between.gap-4
          [:h2.heading-2 "Every version, ever"]
          [:input.input.max-w-48
           {:type "search"
            :id "q"
            :name "q"
            :placeholder "Search…"
            :hx-trigger "input changed delay:100ms, search"
            :hx-get (str 'history-items)
            :hx-target "#history-items"}]]

         [:div.mt-6
          {:id "history-items"}
          (history-items req nil)]]))))

(su/defcomponent ^:endpoint person-history [req]
  show-user
  (let [{:person/keys [first-name last-name email]}
        (or (find-person (xt/db (:db-node req)))
            {:person/first-name "John"
             :person/last-name "Doe"
             :person/email "john.doe@example.com"})]
    (show-user req first-name last-name email)))
