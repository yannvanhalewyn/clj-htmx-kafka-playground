(ns my-app.web.ui.chat
  (:require
    [hiccup2.core :as h]
    [my-app.tools.ui :as ui]
    [my-app.web.sse :as sse]
    [my-app.web.ui.layout :as layout]
    [simpleui.core :as su]))

(su/defcomponent ^:endpoint chat-message [req message]
  (sse/send! req
    {:sse/event "message"
     :sse/data (str (h/html [:p message]))
     :sse/topic "chat"})
  [:p message])

(defn chat-message-input
  "A chat message input component that submits on Enter and allows multiline
  with Shift+Enter"
  [& {:keys [placeholder endpoint target swap]
      :or {placeholder "Type your message..."
           endpoint "/api/messages"
           target "#chat-messages"
           swap "beforeend"}}]
  [:form
   {:hx-post endpoint
    :hx-target target
    :hx-swap swap}
   [:textarea.input
    {:name "message"
     :placeholder placeholder
     :rows "1"
     :class "w-full p-3 pr-12 border border-gray-300 rounded-lg resize-none focus:ring-2 focus:ring-blue-500 focus:border-transparent outline-none"
     :style "min-height: 2.5rem; max-height: 8rem;"
     :_ "on keydown
           if event.key is 'Enter' and not event.shiftKey
             halt the event
             trigger submit on closest <form/>
             set my value to ''
           end
         end
         on input
           set my style.height to 'auto'
           set my style.height to (my scrollHeight + 'px')
         end"}]])

(su/defcomponent ^:endpoint chat [req]
  chat-message
  (let [sse-session {:sse/topic "chat"}]
    (layout/if-page-load req
      (ui/link "/" "← Back")
      (sse/hx-listener sse-session)
      [:div.mt-4
       [:div#chat-messages]
        ;;{:sse-swap "message"
        ;; :hx-swap "beforeend"}]
       [:div
        {:sse-swap "message"
         :hx-swap "beforeend"}]
       [:div.mt-4
        [:label.font-bold "Write your message"]
        (chat-message-input
          {:endpoint "chat-message"
           :target "#chat-messages"
           :swap "beforeend"})]])))
