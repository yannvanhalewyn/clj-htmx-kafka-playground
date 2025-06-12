(ns my-app.archive
  (:require
    [clojure.tools.logging :as log]
    [integrant.core :as ig]
    [jackdaw.client :as jc]
    [my-app.tools.etl-topology :as etl-topology]
    [my-app.tools.kafka :as kafka]
    [xtdb.api :as xt])
  (:import
    [org.apache.kafka.clients.consumer ConsumerRecord ConsumerRecords]))

(defn- process-payloads!
  "Takes records from the raw-payloads consumer, extracts metadata and then
  adds a message to the processed objects"
  [{:keys [my-app.db/db-node connector/consumer connector/producers]} records]
  (log/debug "[Process] Processing payloads: " (.count ^ConsumerRecords records))
  (let [producer (get producers :topic/processed-objects)]
   (doseq [^ConsumerRecord record records]
     (try
       (let [payload (.value record)]
         (log/debug "[Process] Processing payload " (:object-id payload))
         (xt/submit-tx db-node
           [[::xt/put (assoc payload :xt/id (random-uuid))]])
         (jc/produce! (:producer producer) (:topic producer) (.key record)
           {:object-id (:object-id payload)})
         ;; Commiting after every record lowers throughput, consider
         ;; committing in smaller batches
         (kafka/commit-record! consumer record))
       (catch Exception e
         (log/error e "[Process] Failed to process payload:" (.key record))
         ;; Send to dead letter queue
         (throw e))))))

(defn- notify-user!
  [_ records]
  (doseq [^ConsumerRecord record records]
    (log/warn "[Notify user] Object received: " (:object-id (.value record)))))

(defmethod ig/init-key ::archiving-pipeline
  [_ config]
  (-> (etl-topology/new config)
    (etl-topology/add-source :source/payload-ingester :topic/raw-payloads)
    (etl-topology/add-connector :connector/process-payloads
      {:in [:topic/raw-payloads]
       :out [:topic/processed-objects]}
      process-payloads!)
    (etl-topology/add-sink :sink/notify-user
      {:in [:topic/processed-objects]}
      notify-user!)
    (etl-topology/start!)))

(defmethod ig/halt-key! ::archiving-pipeline
  [_ topology]
  (etl-topology/stop! topology))

(comment
  (user/delete-all-entities!)
  (count
   (xt/q (user/db)
     '{:find [(pull ?e [*])]
       :where [[?e :xt/id]]}))

  (xt/q (user/db)
    '{:find [(pull ?e [*])]
      :where [[?e :object-id "object-297"]]})

  (def ids
    (set
     (map first
      (xt/q (user/db)
        '{:find [?object-id]
          :where [[?e :object-id ?object-id]]}))))

  (def all-ids
    (into #{}
     (for [n (range 1000)]
       (format "object-%03d" n))))
  (clojure.set/difference all-ids ids)

  (def producer
    (let [topic (#'etl-topology/topic-config {:topic-name "dev-etl-raw-payloads"})]
      {:topic topic
       :producer
       (jc/producer {"bootstrap.servers" "localhost:29092"} topic)}))

  (dotimes [n 10]
    (let [object-id (format "object-%03d" n)]
     (jc/produce! (:producer producer) (:topic producer)
       object-id
       {:object-id object-id
        :time #inst "2025-06-10T12:25:40.000-00:00"
        :content-type "application/json"
        :data "{\"foo\": \"bar\"}"
        :bsn "10"}))))
