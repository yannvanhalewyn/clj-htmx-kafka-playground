(ns my-app.events
  (:require
    [clojure.tools.logging :as log]
    [integrant.core :as ig]
    [jackdaw.admin :as ja]
    [jackdaw.client :as jc]
    [jackdaw.serdes.edn :as jedn]
    [malli.core :as m]
    [malli.error :as me]
    [my-app.tools.date :as date])
  (:import
    [java.time Duration Instant]
    [org.apache.kafka.clients.consumer ConsumerRecord]
    [org.apache.kafka.common.errors WakeupException]))

(defmethod print-method java.time.Instant
  [inst ^java.io.Writer w]
  (.write w (str "#instant \"" (.toString inst) "\"")))

(defn- edn-serde []
  (jedn/serde {:readers {'instant Instant/parse}}))

(defmulti process-batch! :handler)

(defmethod process-batch! :my-app.events/calculate-flight-durations
  [{:keys [producer]} records]
  (let [processed-count (atom 0)
        error-count (atom 0)]
    (doseq [^ConsumerRecord record records]
      (log/debug "Producing flight duration record")
      (try
        (jc/produce! (:producer producer) (:topic producer) (.key record)
          ;; TODO calculate duration, can't do it without joining stream...
          {:duration 10})
        (swap! processed-count inc)
        (catch Exception e
          (log/error e "Failed to calculate duration:" (.key record))
          (swap! error-count inc))))
    {:processed @processed-count
     :errors @error-count
     :total (.count records)}))

(defmethod process-batch! :my-app.events/warn-delayed-flights
  [_ records]
  (log/debug "Processing delayed flight warnings: " (.count records))
  (doseq [^ConsumerRecord record records]
    (let [payload (.value record)]
      (case (:event-type payload)
        :flight-departed
        (when (> (inst-ms (:time payload))
                (inst-ms (:scheduled-departure payload)))
          (log/warnf "Delayed flight: [%s] departed at %s while scheduled at %s"
            (:flight payload)
            (date/format-date (:time payload))
            (date/format-date (:scheduled-departure payload))))
        :flight-arrived
        (when (> (inst-ms (:time payload))
                (inst-ms (:scheduled-arrival payload)))
          (log/warnf "Delayed flight: [%s] arrived at %s while scheduled at %s"
            (:flight payload)
            (date/format-date (:time payload))
            (date/format-date (:scheduled-arrival payload))))
        (log/debug "Different event type" payload)))))

(defmethod ig/init-key :my-app.kafka/config
  [_ config]
  config)

(defn- start-etl-consumer! [component {:keys [topics kafka-config]}]
  (let [{:keys [group-id subscriptions poll-duration]} (:consumer component)
        consumer (jc/subscribed-consumer
                   (assoc (select-keys kafka-config ["bootstrap.servers"])
                     "group.id" group-id)
                   (map topics subscriptions))
        running? (atom true)]
    (log/info "Starting etl processor for " (:handler component))
    (future
      (try
        (while @running?
          (let [records (.poll consumer (Duration/ofMillis poll-duration))]
            (when (pos? (.count records))
              (log/info "Processing batch of" (.count records) "records")
              (let [result (process-batch! component records)]
                (log/info "Batch processed:" result)
                (.commitSync consumer)
                (log/debug "Committed offsets")))))

        (catch WakeupException _e
          (log/info "ETL processor woken up. Shutting down"))

        (catch Exception e
          (log/error e "ETL processor error. Shutting down"))

        (finally
          (log/info "ETL processor stopped. Shutting down")
          (log/debug "Closing consumer")
          (.close consumer))))

    (assoc component
      :consumer consumer
      :running? running?)))

(defn- assoc-producer [component kafka-config topics]
  (let [topic (and (:producer component)
                   (get topics (:producer component)))]
    (cond-> component
      topic
      (assoc :producer
        {:producer (jc/producer kafka-config topic)
         :topic topic}))))

(defn- topic-config [config]
  (assoc config
    :key-serde (edn-serde)
    :value-serde (edn-serde)))

(defmethod ig/init-key :my-app/etl-topology
  [_ {:keys [kafka-config topics components]}]
  (log/debug "INIT TOPOLOGY" kafka-config topics)
  ;;(ja/create-topics! admin-client (vals topic))
  (let [topics (update-vals topics topic-config)]
    {:components
     (doall
       (for [component components]
         (case (:type component)
           :producer
           ;; I think these need to be closed? So maybe need to be integrant
           ;; components
           (assoc-producer component kafka-config topics)
           :consumer
           (-> component
             (assoc-producer kafka-config topics)
             (start-etl-consumer!
               {:kafka-config kafka-config
                :topics topics})))))}))

(defmethod ig/halt-key! :my-app/etl-topology
  [_ topology]
  (doseq [component (:components topology)]
    (when (:producer component)
      (log/debug "Closing producer" (get-in component [:producer :topic :topic-name])))
    (some-> (:producer component) :producer (.close))
    (when (:consumer component)
      (log/debug "Waking consumer"))
    (some-> (:consumer component) (.wakeup))
    (when (:running? component)
      (log/debug "Setting running? to false"))
    (some-> (:running? component) (reset! false))))

(def FlightEvent
  [:map
   [:flight :string]
   [:event-type [:enum :flight-departed :flight-arrived :passenger-boarded]]
   [:time inst?]
   ;; For passenger events
   [:who {:optional? true} :string]
   ;; For departure events
   [:scheduled-departure {:optional? true} inst?]
   ;; For departure events
   [:scheduled-arrival {:optional? true} inst?]])

(defn validate-flight-event!
  [payload]
  (when-not (m/validate FlightEvent payload)
    (throw (ex-info "Invalid event"
             {:errors (me/humanize (m/explain FlightEvent payload))
              :payload payload})))
  payload)

(defn ingest-data!
  "Helper to ingest raw data into the archive topic"
  [producer topic key data]
  (validate-flight-event! data)
  (jc/produce! producer topic key data)
  (log/info "Ingested data:" key)
  :ok)

(comment
  (require '[my-app.tools.jackdaw :as tools.jackdaw])
  ;; Setup
  (def config (my-app.config/system-config {:profile :dev}))
  (def admin-client (ja/->AdminClient (:my-app.kafka/config config)))

  (ja/list-topics admin-client)
  (ja/delete-topics! admin-client
    [{:topic-name "ledger-entries-requested"}])
  (tools.jackdaw/re-delete-topics (:my-app.kafka/config config)
    #"^dev-etl.*")

  (def producer
    (-> (:my-app/etl-topology integrant.repl.state/system)
      (:components)
      (first)
      :producer))

  (def producer
    (let [topic (topic-config {:topic-name "dev-etl-flight-events"})]
      {:topic topic
       :producer
       (jc/producer {"bootstrap.servers" "localhost:29092"}
         (topic-config {:topic-name "dev-etl-flight-events"}))}))

  (jc/produce! (:producer producer) (:topic producer)
    {:flight "UA102"}
    {:flight "UA102"
     :event-type :flight-departed
     :time #inst "2025-06-10T12:25:40.000-00:00"
     :scheduled-departure #inst "2025-06-10T09:25:40.000-00:00"})

  (jc/produce! (:producer producer) (:topic producer)
    {:flight "UA102"}
    {:flight "UA102"
     :event-type :flight-arrived
     :time #inst "2025-06-10T12:25:40.000-00:00"
     :scheduled-arrival #inst "2025-06-10T11:25:40.000-00:00"}))
