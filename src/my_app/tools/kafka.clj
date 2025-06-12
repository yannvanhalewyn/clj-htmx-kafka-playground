(ns my-app.tools.kafka
  (:import
   [org.apache.kafka.clients.consumer ConsumerRecord KafkaConsumer OffsetAndMetadata]
   [org.apache.kafka.common TopicPartition]))

(defn commit-record!
  "Commits the offset for a single ConsumerRecord to avoid reprocessing on failure.
  Takes a consumer and a ConsumerRecord, commits the next offset to read."
  [^KafkaConsumer consumer ^ConsumerRecord record]
  (let [offset-map (doto (java.util.HashMap.) ;; Done this way to avoid reflection
                     (.put (TopicPartition. (.topic record) (.partition record))
                           (OffsetAndMetadata. (inc (.offset record)))))]
    (.commitSync consumer offset-map)))
