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

;;Takes records from the raw-payloads consumer, extracts metadata and then
;;adds a message to the processed objects
(defmethod etl-topology/process-batch! ::process-payloads
  [{:keys [my-app.db/db-node]} {::etl-topology/keys [consumer producers]} ^ConsumerRecords records]
  (let [producer (get producers :topic/processed-objects)]
    (doseq [^ConsumerRecord record records]
      (try
        (let [payload (.value record)]
          (xt/submit-tx db-node
            [[::xt/put (assoc payload :xt/id (random-uuid))]
             [::xt/put {:xt/id :kafka-offset :offset (.offset record)}]])
          (jc/produce! (::etl-topology/producer producer) (::etl-topology/topic producer)
            (.key record)
            {:object-id (:object-id payload)})
          (kafka/commit-record! consumer record))
        (catch Exception e
          (throw e))))))

(defmethod etl-topology/process-batch! ::notify-users
  [_topology {::etl-topology/keys [consumer]} records]
  (doseq [^ConsumerRecord record records]
    (log/warn "[Notify user] Object processed: " (:object-id (.value record)))
    (kafka/commit-record! consumer record)))

(defmethod ig/init-key ::archiving-pipeline
  [_ config]
  (when (seq config)
    (etl-topology/start! (etl-topology/new config))))

(defmethod ig/halt-key! ::archiving-pipeline
  [_ topology]
  (when topology
    (etl-topology/stop! topology)))

(comment
  (user/delete-all-entities!)

  (count
    (xt/q (user/db)
      '{:find [(pull ?e [*])]
        :where [[?e :xt/id]]}))

  (xt/q (user/db)
    '{:find [(pull ?e [*])]
      :where [[?e :object-id "object-117"]]})

  (def topology
    (::archiving-pipeline integrant.repl.state/system))

  (etl-topology/send! topology ::payload-ingester
    "object-01"
    {:object-id "object-01"
     :time #inst "2025-06-10T12:25:40.000-00:00"
     :content-type "application/json"
     :data "{\"foo\": \"bar\"}"
     :bsn "10"})

  (def producer
    (let [topic (#'etl-topology/topic-config {:topic-name "dev-etl-raw-payloads"})]
      {:topic topic
       :producer
       (jc/producer {"bootstrap.servers" "localhost:29092"} topic)}))

  (dotimes [n 500]
    (let [object-id (format "object-%03d" n)]
      (jc/produce! (:producer producer) (:topic producer)
        object-id
        {:object-id object-id
         :time #inst "2025-06-10T12:25:40.000-00:00"
         :content-type "application/json"
         :data "{\"foo\": \"bar\"}"
         :bsn "10"}))))
