(ns my-app.tools.etl-topology
  (:require
    [clojure.tools.logging :as log]
    [jackdaw.client :as jc]
    [jackdaw.serdes.edn :as jedn]
    [malli.core :as m]
    [malli.util :as mu]
    [my-app.tools.kafka :as kafka]
    [my-app.tools.utils :as u])
  (:import
    [java.time Duration Instant]
    [org.apache.kafka.clients.consumer ConsumerRecords KafkaConsumer]
    [org.apache.kafka.clients.producer KafkaProducer]
    [org.apache.kafka.common.errors WakeupException]))

(def TopicRef :keyword)
(def ComponentRef :keyword)

(def TopicConfig
  (m/schema
    [:map
     [:topic-name :string]
     [:partition-count {:optional true} :int]
     [:replication-factor {:optional true} :int]
     [:topic-config {:optional true} :map]]))

(def ConsumerConfig
  (m/schema
    [:map
     [:poll-duration :int]
     [:batch-size {:optional true} :int]
     [:group-id {:optional true} :string]]))

(def Source
  (m/schema
    [:map
     [:type [:enum :source]]
     [:key ComponentRef]
     [:topic TopicRef]]))

(def PreppedSource
  (mu/merge Source
    [:map
     [::producer {:optional true} [:fn kafka/producer?]]
     [::topic {:optional true} TopicConfig]]))

(def Processor
  (m/schema
    [:map
     [:type [:enum :processor]]
     [:key ComponentRef]
     [:in [:vector TopicRef]]
     [:out [:vector TopicRef]]
     [:consumer-config ConsumerConfig]]))

(def PreppedProcessor
  (mu/merge Processor
    [:map
     [::consumer [:fn kafka/consumer?]]
     [::producers
      [:map-of
       :keyword
       [:map
        [::producer [:fn kafka/producer?]]
        [::topic TopicConfig]]]]]))

(def Sink
  (m/schema
    [:map
     [:type [:enum :sink]]
     [:key ComponentRef]
     [:in [:vector TopicRef]]
     [:consumer-config ConsumerConfig]]))

(def PreppedSink
  (mu/merge Sink
    [:map
     [::consumer [:fn kafka/consumer?]]]))

(def Component
  [:or Source Processor Sink])

(def PreppedComponent
  [:or PreppedSource PreppedProcessor PreppedSink])

(def TopologyConfig
  (m/schema
    [:map
     [:kafka-config [:map-of :string :string]]
     [:topics [:map-of TopicRef TopicConfig]]
     [:components [:vector Component]]]))

(def Topology
  (m/schema
    [:map
     [::kafka-config [:map-of :string :string]]
     [::topics [:map-of TopicRef TopicConfig]]
     [::components [:map-of ComponentRef PreppedComponent]]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Building the toplogy

(defn- edn-serde []
  (jedn/serde {:readers {'instant Instant/parse}}))

(defn- topic-config [config]
  (assoc config
    :key-serde (edn-serde)
    :value-serde (edn-serde)))

(defn- consumer-config*
  [kafka-config {:keys [key consumer-config]}]
  (u/assoc-some (merge (select-keys kafka-config ["bootstrap.servers"])
                  (u/filter-keys string? consumer-config))
    "group.id" (:group-id consumer-config)
    "client.id" (str "consumer-" (subs (str key) 1))
    "max.poll.records" (some-> (:batch-size consumer-config) int)
    "enable.auto.commit" "false"))

(defn- producer-config
  [kafka-config k]
  (merge kafka-config
    {"acks" "all"
     "client.id" (str "producer-" (subs (str k) 1))}))

(defmulti prep-component
  (fn [_topology component]
    (:type component)))

(defmethod prep-component :source
  [{::keys [kafka-config topics]} component]
  (let [topic (get topics (:topic component))
        config (producer-config kafka-config (:key component))]
    (assoc component
      ::producer (jc/producer config topic)
      ::topic topic)))

(defmethod prep-component :processor
  [{::keys [kafka-config topics]} {:keys [in out] :as component}]
  (assoc component
    ::consumer
    (jc/subscribed-consumer
      (consumer-config* kafka-config component)
      (map topics in))

    ::producers
    (into {}
      (for [topic-key out
            :let [topic (get topics topic-key)]]
        [topic-key
         {::topic topic
          ::producer (jc/producer (producer-config kafka-config topic-key) topic)}]))))

(defmethod prep-component :sink
  [{::keys [topics kafka-config]} {:keys [in] :as component}]
  (assoc component ::consumer
    (jc/subscribed-consumer
      (consumer-config* kafka-config component)
      (map topics in))))

(defn- assoc-prepped-components
  "Prepares the components, meaning instantiating the consumers and
  producers, and assoc them onto the topology."
  [topology components]
  (assoc topology ::components
    (u/build-index :key
      (for [component components]
        (prep-component topology component)))))

(defn new [{:keys [kafka-config topics] :as config}]
  (-> (assoc (dissoc config :kafka-config :topics :components)
        ::kafka-config kafka-config
        ::topics (update-vals topics topic-config))
    (assoc-prepped-components (:components config))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Running the topology

(defmulti process-batch!
  (fn [_topology component _records]
    (:key component)))

(defn- start-polling!
  [topology component]
  (let [consumer ^KafkaConsumer (::consumer component)
        poll-duration (or (get-in component [:consumer-config :poll-duration])
                          1000)
        running? (atom true)]
    (future
      (try
        (while @running?
          (let [records ^ConsumerRecords (.poll consumer (Duration/ofMillis poll-duration))]
            (when (pos? (.count records))
              ;;(log/info "Processing batch of" (.count records) "records")
              (let [result (process-batch! topology component records)]
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
  (update topology ::components update-vals
    (fn [component]
      (case (:type component)
        (:processor :sink) (start-polling! topology component)
        component))))

(defn stop! [topology]
  (doseq [component (vals (::components topology))]
    (log/debug "Shutting down component:" (:key component))

    (when-let [producer (::producer component)]
      (.close ^KafkaProducer producer))

    (when-let [producers (::producers component)]
      (doseq [producer (vals producers)]
        (.close ^KafkaProducer (::producer producer))))

    (when-let [running? (::running? component)]
      (reset! running? false))

    (when-let [consumer (::consumer component)]
      (log/debug "Waking consumer" consumer)
      (.wakeup ^KafkaConsumer consumer))))

(defn send!
  "Sends a record to a source consumer of the topology"
  [topology source-key key data]
  (let [{::keys [producer topic]} (get-in topology [::components source-key])]
    (jc/produce! producer topic key data))
  (log/info "Ingested data:" key)
  :ok)
