(ns my-app.web.ui.delayed-render-sse
  (:require
   [clojure.core.async :as a]
   [hiccup2.core :as h]
   [my-app.tools.ui :as ui]
   [my-app.web.async-render :as async-render]
   [my-app.web.sse :as sse]
   [my-app.web.ui.layout :as layout]
   [simpleui.core :as su]))

(def SSE_TOPIC_NAME "delayed-render-component")

(def info
  {:path "delayed-render"
   :emoji "🐢"
   :title "Delayed Render"
   :blurb "The page shows up instantly. Slow data streams in when it's ready."
   :intro "Like React's Suspense, but on the server. The page is sent right
           away with placeholders, and each slow section is pushed over a
           single Server-Sent Events connection as soon as it's done."
   :attrs ["hx-ext=\"sse\"" "sse-connect" "sse-swap" "hx-swap"]
   :steps
   [(list "The page renders immediately with placeholders. A wrapper with "
          (ui/code "sse-connect=\"/sse?…\"") " opens one SSE connection for
          this visit.")
    (list "On the server each section runs in a " (ui/code "future")
          ". When it finishes, the rendered HTML is sent as an SSE event named
          after the section, e.g. " (ui/code "users") ".")
    (list "The placeholder has " (ui/code "sse-swap=\"users\"")
          ", so HTMX swaps it for that event's HTML. The stream uses "
          (ui/code "hx-swap=\"beforeend\"") " to append instead.")
    (list "Navigate away and the connection closes. The server notices and
          cancels any work that's still pending.")]})

(defn- get-users []
  (for [i (map inc (range 5))]
    {:name (str "User " i)
     :email (str "user" i "@example.com")}))

(defn- get-comments []
  (for [i (map inc (range 10))]
    {:comment (str "Comment " i)}))

(defn- get-events []
  (for [i (map inc (range 20))]
    {:name (str "Event " i)}))

(defn- section [title & children]
  (into
    [:div.space-y-3
     [:h2.heading-2 title]]
    children))

(defn- placeholder
  "Skeleton lines with a bar that fills up over the expected wait time"
  [label seconds]
  [:div.space-y-3
   [:p.text-sm.text-lavender label " (~" seconds "s)"]
   [:div.countdown
    [:div {:style (str "animation-duration: " seconds "s")}]]
   [:div.space-y-2.animate-pulse
    [:div.skeleton {:class "w-3/4"}]
    [:div.skeleton {:class "w-1/2"}]
    [:div.skeleton {:class "w-2/3"}]]])

(su/defcomponent ^:endpoint delayed-render [req]
  (let [sse-session (sse/new-session SSE_TOPIC_NAME)]
    (layout/if-page-load req
      (layout/demo
        (assoc info :code [#'delayed-render
                           #'async-render/suspense
                           #'async-render/stream])
        (sse/hx-listener sse-session
          [:div.space-y-8
           (section "👥 Users"
             (async-render/suspense req
               {:sse-session sse-session
                :key "users"
                :placeholder (placeholder "Fetching users" 5)}
               (future
                 (Thread/sleep 5000)
                 [:ul.animate-fade-in.space-y-1
                  (for [user (get-users)]
                    [:li (:name user) " "
                     [:span.text-lavender "(" (:email user) ")"]])])))

           (section "💬 Comments"
             (async-render/suspense req
               {:sse-session sse-session
                :key "comments"
                :placeholder (placeholder "Fetching comments" 2)}
               (future
                 (Thread/sleep 2000)
                 [:ul.animate-fade-in.space-y-1
                  (for [c (get-comments)]
                    [:li (:comment c)])])))

           (section "🌊 Streamed events"
             (async-render/stream req
               {:sse-session sse-session
                :key "streamed-events"
                :hx-swap "beforeend"
                :class "flex flex-wrap gap-2"}
               (fn [hiccup-ch done-fn]
                 (a/go-loop [[cur & others] (get-events)]
                   (a/<! (a/timeout 500))
                   (if cur
                     ;; Returns nil when channel is closed
                     ;; A closed channel means the client left.
                     (when (a/>! hiccup-ch [:span.chip.animate-fade-in (:name cur)])
                       (recur others))
                     (do
                       (a/>! hiccup-ch [:span.chip.animate-fade-in
                                        {:class "border-mint/40 bg-mint/15 text-mint"}
                                        "✓ Stream complete"])
                       (done-fn)))))))])))))

(comment
  (defn replace-body [msg]
    (sse/send! (user/sse-listener)
      {:sse/event "comments"
       :sse/data (str (h/html [:p msg]))
       :sse/topic SSE_TOPIC_NAME
       ;; Get session from browser connection or logs
       :sse/session-id #uuid "e73961a4-1cb1-4001-a464-c4dff32df1a5"}))
  (replace-body "Hello!")
  (replace-body "Replace async"))
