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
    [my-app.tools.kafka :as kafka]
    [xtdb.api :as xt])
  (:import
    [org.apache.kafka.clients.consumer ConsumerRecord ConsumerRecords]))

(set! *warn-on-reflection* true)

(defmethod print-method java.time.Instant
  [^java.time.Instant inst ^java.io.Writer w]
  (.write w (str "#instant \"" (.toString inst) "\"")))

(defmethod etl-topology/process-batch! :processor/flight-durations
  [_topology {::etl-topology/keys [producers]} ^ConsumerRecords records]
  (let [processed-count (atom 0)
        error-count (atom 0)
        producer (get producers :topic/flight-durations)]
    (doseq [^ConsumerRecord record records]
      (log/debug "[Durations] Producing flight duration record")
      (try
        (jc/produce! (::etl-topology/producer producer) (::etl-topology/topic producer) (.key record)
          ;; TODO calculate duration, can't do it without joining stream...
          {:duration 10})
        (swap! processed-count inc)
        (catch Exception e
          (log/error e "[Durations] Failed to calculate duration:" (.key record))
          (swap! error-count inc))))
    {:processed @processed-count
     :errors @error-count
     :total (.count records)}))

(defmethod etl-topology/process-batch! :sink/duration-warnings
  [_topology _component records]
  (log/debug "[Duration Warning] Processing delayed flight warnings: " (.count ^ConsumerRecords records))
  (doseq [^ConsumerRecord record records]
    (let [payload (.value record)]
      (case (:event-type payload)
        :flight-departed
        (when (> (inst-ms (:time payload))
                (inst-ms (:scheduled-departure payload)))
          (log/warnf "[Duration Warning] Delayed flight: [%s] departed at %s while scheduled at %s"
            (:flight payload)
            (date/format-date (:time payload))
            (date/format-date (:scheduled-departure payload))))
        :flight-arrived
        (when (> (inst-ms (:time payload))
                (inst-ms (:scheduled-arrival payload)))
          (log/warnf "[Duration Warning] Delayed flight: [%s] arrived at %s while scheduled at %s"
            (:flight payload)
            (date/format-date (:time payload))
            (date/format-date (:scheduled-arrival payload))))
        (log/debug "[Duration Warning] Different event type" payload)))))

(defmethod etl-topology/process-batch! :sink/copy-to-xtdb
  [{:keys [my-app.db/db-node]} {::etl-topology/keys [consumer]} records]
  (log/debug "[Copy to XTDB] Processing copy to XTDB: " (.count ^ConsumerRecords records))
  (doseq [^ConsumerRecord record records]
    (try
      (let [payload (.value record)]
        (log/debug "[Copy to XTDB]  Processing flight " (:flight payload))
        (xt/submit-tx db-node
          [[::xt/put (assoc payload :xt/id (random-uuid))]])
        (kafka/commit-record! consumer record))
      (catch Exception e
        (log/error e "[Copy to XTDB] Failed to copy to XTDB:" (.key record))
        (throw e)))))

(defmethod ig/init-key ::kafka-config
  [_ config]
  config)

(defmethod ig/init-key ::flights-pipeline
  [_ config]
  (etl-topology/start!
    (etl-topology/new config)))

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
  (tools.jackdaw/re-delete-topics (::kafka-config config)
    #"^dev-etl.*")

  (count
   (xt/q (user/db)
     '{:find [(pull ?e [*])]
       :where [[?e :xt/id]]}))

  (let [cursor (xt/open-tx-log (user/db-node) 7091 true)]
    (try
      (take 4 (iterator-seq (:lazy-seq-iterator cursor)))
      (finally
        ((:close-fn cursor)))))

  (xt/q (user/db)
   '{:find [(pull ?e [*])]
     :where [[?e :flight "UA314"]]})

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

  (etl-topology/send! topology :source/flight-events
    {:flight "UA102"}
    {:flight "UA102"
     :event-type :flight-departed
     :time #inst "2025-06-10T12:25:40.000-00:00"
     :scheduled-departure #inst "2025-06-10T09:25:40.000-00:00"})

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

  (dotimes [n 400]
    (let [flightnr (format "UA%03d" n)]
     (jc/produce! (:producer producer) (:topic producer)
       {:flight flightnr}
       {:flight flightnr
        :event-type :flight-departed
        :time #inst "2025-06-10T12:25:40.000-00:00"
        :scheduled-departure #inst "2025-06-10T12:25:40.000-00:00"}))))
