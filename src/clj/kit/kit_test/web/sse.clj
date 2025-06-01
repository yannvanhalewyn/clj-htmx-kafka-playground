(ns kit.kit-test.web.sse
  (:require
    [clojure.core.async :as a]
    [integrant.core :as ig]
    [kit.kit-test.tools.request :as request]
    [kit.kit-test.tools.response :as response]
    [kit.kit-test.tools.utils :as u]
    [malli.core :as m]
    [malli.transform :as mt]
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

(defn- event-stream-msg [{:keys [sse/event sse/data]}]
  (str "event:" event response/EOL
       "data:" data response/EOL
       response/EOL))

(defn- start-listener! []
  (let [events-chan (a/chan 10000)
        ;; TODO each login session adds a new key and never gets cleared
        sessions (atom {})]
    (a/go-loop []
      (when-let [sse-msg (a/<! events-chan)]
        (doseq [output-ch (get-in @sessions (session-path sse-msg))]
          (a/>! output-ch (event-stream-msg sse-msg)))
        (recur)))
    {::events-chan events-chan
     :handler
     (fn register-client [req]
       (let [query-params (decode-session-query-params (:query-params req))
             session-path (session-path
                            {:sse/topic (:topic query-params)
                             :sse/session-id (:session-id query-params)})
             output-ch (a/chan 100)
             cleanup (fn []
                       (a/close! output-ch)
                       (swap! sessions update-in session-path
                         (fn [output-channels]
                           (remove #(= % output-ch) output-channels))))]
         (swap! sessions update-in session-path conj output-ch)
         (response/event-stream-response output-ch {:cleanup cleanup})))}))

(defmethod ig/init-key ::sse-listener
  [_ _config]
  (let [{::keys [events-chan] :keys [handler]} (start-listener!)]
    {::events-chan events-chan
     :middleware [[request/wrap-assoc ::events-chan events-chan]]
     :routes [["/sse"
               {:name :route/sse
                :public? true
                :get handler}]]}))

(defmethod ig/halt-key! ::sse-listener
  [_ config]
  (a/close! (::events-chan config)))

(defn send! [req sse-event]
  (a/>!! (::events-chan req) sse-event))
