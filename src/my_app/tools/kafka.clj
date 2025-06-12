(ns my-app.tools.kafka
  (:require
    [clojure.tools.logging :as log])
  (:import
    [java.util Collection]
    [org.apache.kafka.clients.admin AdminClient NewTopic]
    [org.apache.kafka.clients.consumer ConsumerRecord KafkaConsumer OffsetAndMetadata]
    [org.apache.kafka.clients.producer KafkaProducer]
    [org.apache.kafka.common TopicPartition]))

(defn make-admin-client ^AdminClient
  [^java.util.Map config]
  (AdminClient/create config))

(defn list-topics
  [^AdminClient admin-client]
  (set @(.names (.listTopics admin-client))))

(defn- new-topic
  [{:keys [topic-name partition-count replication-factor topic-config]}]
  (cond-> (NewTopic. ^String topic-name (int partition-count) (short replication-factor))
    topic-config (.configs topic-config)))

(defn create-topics! [^AdminClient admin-client topic-configs]
  (log/info "Creating Kafka topics" (map :topic-name topic-configs))
  (let [topics (map new-topic topic-configs)]
    @(.all (.createTopics admin-client ^Collection topics))))

(defn producer? [x]
  (instance? KafkaProducer x))

(defn consumer? [x]
  (instance? KafkaConsumer x))

(defn commit-record!
  "Commits the offset for a single ConsumerRecord to avoid reprocessing on failure.
  Takes a consumer and a ConsumerRecord, commits the next offset to read."
  [^KafkaConsumer consumer ^ConsumerRecord record]
  (let [offset-map (doto (java.util.HashMap.) ;; Done this way to avoid reflection
                     (.put (TopicPartition. (.topic record) (.partition record))
                       (OffsetAndMetadata. (inc (.offset record)))))]
    (.commitSync consumer offset-map)))
