(ns my-app.events
  (:require
    [clojure.tools.logging :as log]
    [integrant.core :as ig]
    [jackdaw.admin :as ja]
    [jackdaw.client :as jc]
    [malli.core :as m]
    [malli.error :as me]
    [my-app.tools.date :as date]
    [my-app.tools.etl-topology :as etl-topology]
    [xtdb.api :as xt])
  (:import
    [org.apache.kafka.clients.consumer ConsumerRecord OffsetAndMetadata]))

(defmethod print-method java.time.Instant
  [inst ^java.io.Writer w]
  (.write w (str "#instant \"" (.toString inst) "\"")))

(defn- calculate-flight-durations
  [{:keys [connector/producers]} records]
  (let [processed-count (atom 0)
        error-count (atom 0)
        producer (get producers :topic/flight-durations)]
    (doseq [^ConsumerRecord record records]
      (log/debug "[Durations] Producing flight duration record")
      (try
        (jc/produce! (:producer producer) (:topic producer) (.key record)
          ;; TODO calculate duration, can't do it without joining stream...
          {:duration 10})
        (swap! processed-count inc)
        (catch Exception e
          (log/error e "[Durations] Failed to calculate duration:" (.key record))
          (swap! error-count inc))))
    {:processed @processed-count
     :errors @error-count
     :total (.count records)}))

(defn- warn-delayed-flights
  [_ records]
  (log/debug "[Delayed Flights] Processing delayed flight warnings: " (.count records))
  (doseq [^ConsumerRecord record records]
    (let [payload (.value record)]
      (case (:event-type payload)
        :flight-departed
        (when (> (inst-ms (:time payload))
                (inst-ms (:scheduled-departure payload)))
          (log/warnf "[Delayed Flights] Delayed flight: [%s] departed at %s while scheduled at %s"
            (:flight payload)
            (date/format-date (:time payload))
            (date/format-date (:scheduled-departure payload))))
        :flight-arrived
        (when (> (inst-ms (:time payload))
                (inst-ms (:scheduled-arrival payload)))
          (log/warnf "[Delayed Flights] Delayed flight: [%s] arrived at %s while scheduled at %s"
            (:flight payload)
            (date/format-date (:time payload))
            (date/format-date (:scheduled-arrival payload))))
        (log/debug "[Delayed Flights] Different event type" payload)))))

(defn copy-to-xtdb!
  [{:keys [my-app.db/db-node sink/consumer]} records]
  (log/debug "[Copy to XTDB] Processing copy to XTDB: " (.count records))
  (doseq [^ConsumerRecord record records]
    (try
      (let [payload (.value record)]
        (log/debug "[Copy to XTDB]  Processing flight " (:flight payload))
        (xt/await-tx db-node
          (xt/submit-tx db-node
            [[::xt/put (assoc payload :xt/id (random-uuid))]])))
      (catch Exception e
        (log/error e "[Copy to XTDB] Failed to copy to XTDB:" (.key record))
        (throw e)))))

(defmethod ig/init-key ::kafka-config
  [_ config]
  config)

(defmethod ig/init-key ::flights-pipeline
  [_ config]
  (-> (etl-topology/new config)
    (etl-topology/add-source :source/flight-events :topic/flight-events)
    (etl-topology/add-connector :connector/flight-durations
      {:in [:topic/flight-events]
       :out [:topic/flight-durations]}
      calculate-flight-durations)
    (etl-topology/add-sink :sink/duration-warnings
      {:in [:topic/flight-events]}
      warn-delayed-flights)
    (etl-topology/add-sink :sink/copy-to-xtdb
      {:in [:topic/flight-events]}
      copy-to-xtdb!)
    (etl-topology/start!)))

(defmethod ig/halt-key! ::flights-pipeline
  [_ topology]
  (etl-topology/stop! topology))

(def FlightEvent
  [:map
   [:flight :string]
   [:event-type [:enum :flight-departed :flight-arrived :passenger-boarded]]
   [:time inst?]
   ;; For passenger events
   [:who {:optional true} :string]
   ;; For departure events
   [:scheduled-departure {:optional true} inst?]
   ;; For departure events
   [:scheduled-arrival {:optional true} inst?]])

(defn validate-flight-event!
  [payload]
  (when-not (m/validate FlightEvent payload)
    (throw (ex-info "Invalid event"
             {:errors (me/humanize (m/explain FlightEvent payload))
              :payload payload})))
  payload)

(comment
  (require '[my-app.tools.jackdaw :as tools.jackdaw])
  ;; Setup
  (def config (my-app.config/system-config {:profile :dev}))
  (def admin-client (ja/->AdminClient (::kafka-config config)))

  (ja/list-topics admin-client)
  (ja/delete-topics! admin-client
    [{:topic-name "ledger-entries-requested"}])
  (tools.jackdaw/re-delete-topics (:my-app.kafka/config config)
    #"^dev-etl.*")

  (count
   (xt/q (user/db)
     '{:find [(pull ?e [*])]
       :where [[?e :xt/id]]}))

  (->>
     (xt/q (user/db)
      '{:find [(pull ?e [*])]
        :where [[?e :xt/id]]})
     (map first)
     (map :flight)
     (frequencies)
     (filter
      (fn [[_k v]]
        (> v 1)))
     (sort-by first))
  (user/delete-all-entities!)

  (def topology
    (::flights-pipeline integrant.repl.state/system))

  (dotimes [n 500]
    (let [flightnr (str "UA" n)]
     (etl-topology/send! topology :source/flight-events
       {:flight flightnr}
       {:flight flightnr
        :event-type :flight-departed
        :time #inst "2025-06-10T12:25:40.000-00:00"
        :scheduled-departure #inst "2025-06-10T09:25:40.000-00:00"})))

  (etl-topology/send! topology :source/flight-events
    {:flight "UA102"}
    {:flight "UA102"
     :event-type :flight-arrived
     :time #inst "2025-06-10T12:25:40.000-00:00"
     :scheduled-arrival #inst "2025-06-10T11:25:40.000-00:00"})

  (def producer
    (let [topic (#'etl-topology/topic-config {:topic-name "dev-etl-flight-events"})]
      {:topic topic
       :producer
       (jc/producer {"bootstrap.servers" "localhost:29092"} topic)}))

  (dotimes [n 100]
    (let [flightnr (format "UA%03d" n)]
     (jc/produce! (:producer producer) (:topic producer)
       {:flight flightnr}
       {:flight flightnr
        :event-type :flight-departed
        :time #inst "2025-06-10T12:25:40.000-00:00"
        :scheduled-departure #inst "2025-06-10T09:25:40.000-00:00"}))))
