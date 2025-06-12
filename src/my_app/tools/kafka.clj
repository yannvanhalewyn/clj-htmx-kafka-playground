(ns my-app.tools.kafka
  (:import
   [org.apache.kafka.clients.consumer ConsumerRecord KafkaConsumer OffsetAndMetadata]
   [org.apache.kafka.clients.producer KafkaProducer]
   [org.apache.kafka.common TopicPartition]))

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

(comment
  (.commitSync consumer
   {(TopicPartition. (.topic record) (.partition record))
    (OffsetAndMetadata. (inc (.offset record)))}))

