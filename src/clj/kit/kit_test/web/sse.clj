(ns kit.kit-test.web.sse
  (:require
    [clojure.core.async :as a]
    [integrant.core :as ig]
    [kit.kit-test.tools.request :as request]
    [kit.kit-test.tools.response :as response]
    [malli.core :as m]))

(def SSEMessage
  (m/schema
    [:map
     [:sse/event :string]
     [:sse/data [:maybe :string]]
     [:sse/topic :string]]))

(defn event-stream-msg [{:keys [sse/event sse/data]}]
  (str "event:" event response/EOL
       "data:" data response/EOL
       response/EOL))

(defn- start-listener! []
  (let [events-chan (a/chan 10000)
        ;; TODO each login session adds a new key and never gets cleared
        clients (atom {})]
    (a/go-loop []
      (when-let [sse-event (a/<! events-chan)]
        (doseq [output-ch (get @clients (:sse/topic sse-event))]
          (a/>! output-ch (event-stream-msg sse-event)))
        (recur)))
    {::events-chan events-chan
     :handler
     (fn register-client [req]
       (let [sse-topic (-> req :query-params (get "topic"))
             output-ch (a/chan 100)
             cleanup (fn []
                       (a/close! output-ch)
                       (swap! clients update sse-topic
                         (fn [output-channels]
                           (remove #(= % output-ch) output-channels))))]
         (swap! clients update sse-topic conj output-ch)
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

(defn sse-url [topic-name]
  (str "/sse?topic=" topic-name))

(defn send! [req sse-event]
  (a/>!! (::events-chan req) sse-event))

(defn hx-listener [{:keys [sse/topic]} & children]
  [:div
   {:hx-ext "sse"
    :sse-connect (sse-url topic)}
   children])
