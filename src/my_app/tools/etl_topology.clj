(ns my-app.tools.etl-topology
  (:require
    [clojure.tools.logging :as log]
    [jackdaw.client :as jc]
    [jackdaw.serdes.edn :as jedn]
    [malli.core :as m]
    [my-app.tools.utils :as u])
  (:import
    [java.time Duration Instant]
    [org.apache.kafka.clients.consumer KafkaConsumer ConsumerRecords]
    [org.apache.kafka.clients.producer KafkaProducer]
    [org.apache.kafka.common.errors WakeupException]))

(defn- kafka-producer? [x]
  (instance? KafkaProducer x))

(defn- kafka-consumer? [x]
  (instance? KafkaConsumer x))

(def TopicConfig
  (m/schema
    [:map
     [:topic-name :string]
     [:partition-count {:optional true} :int]
     [:replication-factor {:optional true} :int]
     [:topic-config {:optional true} :map]]))

(def Source
  (m/schema
    [:map
     [:source/producer [:fn kafka-producer?]]
     [:source/topic TopicConfig]]))

(def Connector
  (m/schema
    [:map
     [:connector/consumer [:fn kafka-consumer?]]
     [:connector/producers [:map-of :keyword
                            [:map
                             [:topic TopicConfig]
                             [:producer [:fn kafka-producer?]]]]]
     [:connector/handler fn?]]))

(def Sink
  [:map
   [:sink/consumer [:fn kafka-consumer?]]])

(def Topology
  (m/schema
    [:map
     [::topics [:map-of :keyword TopicConfig]]
     [::kafka-config [:map-of :string :string]]
     [::sources [:map-of :keyword Source]]
     [::connectors [:map-of :keyword Connector]]
     [::sinks [:map-of :keyword Sink]]]))

(defn- edn-serde []
  (jedn/serde {:readers {'instant Instant/parse}}))

(defn- topic-config [config]
  (assoc config
    :key-serde (edn-serde)
    :value-serde (edn-serde)))

(defn new [{:keys [kafka-config topics consumer-configs] :as config}]
  (assoc (dissoc config :kafka-config :topics :consumer-configs)
    ::topics (update-vals topics topic-config)
    ::kafka-config kafka-config
    ::consumer-configs consumer-configs))

(defn- consumer-config [{::keys [kafka-config consumer-configs]} component-key]
  (let [config (get consumer-configs component-key)]
    (u/assoc-some (select-keys kafka-config ["bootstrap.servers"])
      "group.id" (:group-id config)
      "client.id" (str "consumer-" (subs (str component-key) 1))
      "max.poll.records" (some-> (:batch-size config) int))))

(defn- producer-config [{::keys [kafka-config]} component-key]
  (merge kafka-config
    {"acks" "all"
     "client.id" (str "producer-" (subs (str component-key) 1))}))

(defn add-source [topology source-key topic-key]
  (let [topic (get-in topology [::topics topic-key])]
    (assoc-in topology [::sources source-key]
      {:source/producer (jc/producer (producer-config topology source-key) topic)
       :source/topic topic})))

;; Consider renaming to add-processor
(defn add-connector [topology connector-key {:keys [in out]} handler]
  (assoc-in topology [::connectors connector-key]
    {:connector/consumer (jc/subscribed-consumer
                           (consumer-config topology connector-key)
                           (map (::topics topology) in))
     :connector/producers
     (into {}
       (for [topic-key out
             :let [topic (get-in topology [::topics topic-key])]]
         [topic-key
          {:topic topic
           :producer (jc/producer (producer-config topology topic-key) topic)}]))
     :connector/handler handler
     :poll-duration (get-in topology [::consumer-configs connector-key :poll-duration])}))

(defn add-sink [topology sink-key {:keys [in]} handler]
  (assoc-in topology [::sinks sink-key]
    {:sink/consumer (jc/subscribed-consumer (consumer-config topology sink-key)
                      (map (::topics topology) in))
     :sink/handler handler
     :poll-duration (get-in topology [::consumer-configs sink-key :poll-duration])}))

(defn- start-polling!
  [topology component ^KafkaConsumer consumer handler]
  (let [running? (atom true)]
    (future
      (try
        (while @running?
          (let [records ^ConsumerRecords (.poll consumer (Duration/ofMillis (or (:poll-duration component) 1000)))]
            (when (pos? (.count records))
              ;;(log/info "Processing batch of" (.count records) "records")
              (let [result (handler (merge topology component) records)]
                ;;(log/info "Batch processed:" result)
                (.commitSync consumer)))))

        (catch WakeupException _e
          (log/info "ETL processor woken up. Shutting down"))

        (catch Exception e
          (log/error e "ETL processor error. Shutting down"))

        (finally
          (log/info "ETL processor stopped. Shutting down")
          (.close consumer))))

    (assoc component ::running? running?)))

(defn start! [topology]
  (-> topology
    (update ::connectors update-vals
      (fn [{:connector/keys [consumer handler] :as connector}]
        (start-polling! topology connector consumer handler)))
    (update ::sinks update-vals
      (fn [{:sink/keys [consumer handler] :as sink}]
        (start-polling! topology sink consumer handler)))))

(defn stop! [topology]
  (doseq [source (vals (::sources topology))]
    (log/debug "Closing producer" (:source/producer source)
      (get-in source [:source/topic :topic-name]))
    (.close ^KafkaProducer (:source/producer source)))

  (doseq [{::keys [running?] :as connector} (vals (::connectors topology))]
    (log/debug "Waking consumer" (:connector/consumer connector))
    (.wakeup ^KafkaConsumer (:connector/consumer connector))
    (doseq [producer (vals (:connector/producers connector))]
      (log/debug "Closing producer" (:producer producer) (get-in producer [:topic :topic-name]))
      (.close ^KafkaProducer (:producer producer)))
    (reset! running? false))

  (doseq [{::keys [running?] :as sink} (vals (::sinks topology))]
    (log/debug "Waking consumer" (:sink/consumer sink))
    (.wakeup ^KafkaConsumer (:sink/consumer sink))
    (reset! running? false)))

(defn send!
  "Sends a record to a source consumer of the topology"
  [topology source-key key data]
  (let [source (get-in topology [::sources source-key])
        producer (:source/producer source)
        topic (:source/topic source)]
    (jc/produce! producer topic key data))
  (log/info "Ingested data:" key)
  :ok)
