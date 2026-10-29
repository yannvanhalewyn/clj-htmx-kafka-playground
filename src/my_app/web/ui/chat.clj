(ns my-app.web.ui.chat
  (:require
    [clojure.string :as str]
    [hiccup2.core :as h]
    [my-app.tools.ui :as ui]
    [my-app.web.sse :as sse]
    [my-app.web.ui.layout :as layout]
    [simpleui.core :as su]))

(def info
  {:path "chat"
   :emoji "💬"
   :title "Chat"
   :blurb "Open it in two tabs. Messages are broadcast to everyone over SSE."
   :intro "Open this page in a second tab (or browser) and start talking to
           yourself. Sending is a plain form post; receiving is a Server-Sent
           Event that every open tab is listening for."
   :attrs ["hx-post" "hx-swap" "sse-connect" "sse-swap" "_"]
   :steps
   [(list "The textarea uses a little " [:a.text-link {:href "https://hyperscript.org" :target "_blank"} "hyperscript"]
          " (the " (ui/code "_") " attribute) to submit on Enter and grow as
          you type.")
    (list "The form sends " (ui/code "POST chat-message") ". With "
          (ui/code "hx-swap=\"none\"") " HTMX ignores the response. The server
          doesn't reply with the message; it broadcasts it instead.")
    (list "Every tab on this page holds an SSE connection to the "
          (ui/code "chat") " topic. The message list has "
          (ui/code "sse-swap=\"message\"") " and " (ui/code "hx-swap=\"beforeend\"")
          ", so each broadcast is appended, including in your own tab.")]})

(su/defcomponent ^:endpoint chat-message [req message]
  ;; Broadcast to every client on the "chat" topic, including the sender, so
  ;; there's nothing to return.
  (when-not (str/blank? message)
    (sse/send! req
      {:sse/event "message"
       :sse/data (str (h/html [:p.bubble message]))
       :sse/topic "chat"}))
  nil)

(defn chat-message-input
  "A chat message input component that submits on Enter and allows multiline
  with Shift+Enter"
  [& {:keys [placeholder endpoint]
      :or {placeholder "Type your message..."
           endpoint "/api/messages"}}]
  [:form
   {:hx-post endpoint
    :hx-swap "none"}
   [:textarea.input.resize-none
    {:name "message"
     :placeholder placeholder
     :rows "1"
     :style "min-height: 2.75rem; max-height: 8rem;"
     :_ "on keydown
           if event.key is 'Enter' and not event.shiftKey
             halt the event
             trigger submit on closest <form/>
             set my value to ''
             set my style.height to 'auto'
           end
         end
         on input
           set my style.height to 'auto'
           set my style.height to (my scrollHeight + 'px')
         end"}]
   [:p.mt-2.text-xs.text-lavender
    [:kbd "Enter"] " to send · " [:kbd "Shift + Enter"] " for a new line"]])

(su/defcomponent ^:endpoint chat [req]
  chat-message
  (let [sse-session {:sse/topic "chat"}]
    (layout/if-page-load req
      (layout/demo
        (assoc info :code [#'chat #'chat-message #'chat-message-input #'sse/hx-listener])
        (sse/hx-listener sse-session
          [:div.flex.min-h-48.max-h-96.flex-col.gap-2.overflow-y-auto
           {:id "chat-messages"
            :sse-swap "message"
            :hx-swap "beforeend"
            :_ "on htmx:afterSettle set my scrollTop to my scrollHeight"}])
        [:div.mt-6
         [:label.label "Write your message"]
         (chat-message-input
           {:endpoint "chat-message"
            :placeholder "Say something nice…"})]))))
