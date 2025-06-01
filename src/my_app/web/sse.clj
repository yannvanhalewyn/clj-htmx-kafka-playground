(ns my-app.web.sse
  (:require
    [clojure.core.async :as a]
    [clojure.tools.logging :as log]
    [integrant.core :as ig]
    [malli.core :as m]
    [malli.transform :as mt]
    [my-app.tools.async :as async]
    [my-app.tools.request :as request]
    [my-app.tools.response :as response]
    [my-app.tools.utils :as u]
    [ring.util.codec :as ring.codec]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; SSE Message model

(def SSEMessage
  "(schema): A description of what and where to send the SSE message.

  Our internal client state keeps track of topics and optional session-ids.
  Topics are a way of grouping clients together, useful for a specific page.
  Sessions allow users to open a segregated session within that topic, useful
  to model private async updates without notifying all other users. "
  (m/schema
    [:map
     ;; The following keys are used to describe the SSE event to be submitted
     [:sse/event :string]
     [:sse/data [:maybe :string]]

     ;; The following keys are used to described which clients receive the message
     [:sse/topic :string]
     [:sse/session-id {:optional true} :uuid]]))

(def SSESessionQueryParams
  (m/schema
    [:map
     [:topic :string]
     [:session-id {:optional true} :uuid]]))

(defn- decode-session-query-params [params]
  (m/decode SSESessionQueryParams params
    (mt/json-transformer {::mt/keywordize-map-keys true})))

(defn sse-url [session-params]
  (str "/sse?"
       (ring.codec/form-encode
         (u/prune
           {:topic (:sse/topic session-params)
            :session-id (:sse/session-id session-params)}))))

(comment
  (decode-session-query-params
    {"session-id" "bbd36ed1-cee2-41f9-984b-95ff6e8a812d"})
  (sse-url {:sse/topic "test"})
  (sse-url {:sse/session-id (random-uuid)}))

(defn new-session [topic-name]
  {:sse/session-id (random-uuid)
   :sse/topic topic-name})

(defn new-message [sse-session event data]
  (assoc sse-session
    :sse/event event
    :sse/data data))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; HTMX listener

(defn hx-listener [session-params & children]
  [:div
   {:hx-ext "sse"
    :sse-connect (sse-url session-params)}
   children])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Server SSE handler

(defn- session-path
  "The path at which the client channel is stored in the state atom"
  [sse-session]
  [(:sse/topic sse-session) (:sse/session-id sse-session :others)])

(defn- event-stream-msg
  "A text formatted SSE message with event and data."
  [{:keys [sse/event sse/data]}]
  (str "event:" event response/EOL
       "data:" data response/EOL
       response/EOL))

(defn register-cleanup!
  "Register a cleanup function to be ran when the client for the provided
  sse-session disconnects"
  [req sse-session cleanup-key f]
  (swap! (::sessions req) assoc-in
    [::cleanup-handlers (session-path sse-session) cleanup-key]
    f))

(defn unregister-cleanup!
  "Removes the cleanup function for the given sse-session"
  [req sse-session cleanup-key]
  (swap! (::sessions req) u/dissoc-in
    [::cleanup-handlers (session-path sse-session) cleanup-key]))

(comment
 (do
   @(::sessions (user/sse-listener))))

(defn- start-listener! []
  (let [events-chan (a/chan 10000)
        sessions (atom {})]
    (async/pipe-with! events-chan
      (fn [sse-msg]
        (clojure.tools.logging/debug "Received SSE message" sse-msg)
        (let [event-msg (event-stream-msg sse-msg)]
          (doseq [output-ch (get-in @sessions (session-path sse-msg))]
            (clojure.tools.logging/debug "Sending SSE message to " output-ch)
            (a/put! output-ch event-msg)))))
    {::events-chan events-chan
     ::sessions sessions
     :handler
     (fn register-client [req]
       (let [query-params (decode-session-query-params (:query-params req))
             session-path (session-path
                            (u/prune
                              {:sse/topic (:topic query-params)
                               :sse/session-id (:session-id query-params)}))
             output-ch (a/chan 100)
             cleanup (fn []
                       ;; Call custom cleanup functions if any
                       (doseq [[_cleanup-key cleanup-fn]
                               (get-in @sessions [::cleanup-handlers session-path])]
                         (cleanup-fn))

                       ;; Close the channel
                       (a/close! output-ch)

                       ;; Remove sessions from sessions store
                       (swap! sessions update-in session-path
                         (fn [output-channels]
                           (remove #(= % output-ch) output-channels)))

                       ;; Remove remaining registered cleanup handlers
                       (swap! sessions u/dissoc-in [::cleanup-handlers session-path]))]
         (swap! sessions update-in session-path conj output-ch)
         (response/event-stream-response output-ch {:cleanup cleanup})))}))

(defmethod ig/init-key ::sse-listener
  [_ _config]
  (let [{::keys [events-chan sessions] :keys [handler]} (start-listener!)]
    {::events-chan events-chan
     ::sessions sessions
     :middleware [[request/wrap-merge {::events-chan events-chan
                                       ::sessions sessions}]]
     :routes [["/sse"
               {:name :route/sse
                :public? true
                :get handler}]]}))

(defmethod ig/halt-key! ::sse-listener
  [_ config]
  (a/close! (::events-chan config)))

(defn send! [req sse-event]
  (log/debug "Sending SSE event" sse-event)
  (a/>!! (::events-chan req) sse-event))
