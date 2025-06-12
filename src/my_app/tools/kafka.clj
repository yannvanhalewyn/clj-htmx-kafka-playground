(ns my-app.tools.kafka
  (:import
   [org.apache.kafka.clients.consumer ConsumerRecord KafkaConsumer OffsetAndMetadata]
   [org.apache.kafka.common TopicPartition]))

(set! *warn-on-reflection* true)

(defn commit-record!
  "Commits the offset for a single ConsumerRecord to avoid reprocessing on failure.
  Takes a consumer and a ConsumerRecord, commits the next offset to read."
  [^KafkaConsumer consumer ^ConsumerRecord record]
  (.commitSync consumer
    ^java.util.Map
    {(TopicPartition. (.topic record) (.partition record))
     (OffsetAndMetadata. (inc (.offset record)))}))
