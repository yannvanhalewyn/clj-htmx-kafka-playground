(ns my-app.tools.jackdaw
  (:require
    [clojure.tools.logging :as log]
    [my-app.tools.kafka :as kafka])
  (:import
    [java.util Collection]
    [org.apache.kafka.clients.admin AdminClient]))

(defn delete-topics!
  [^AdminClient admin-client ^Collection topics]
  @(.all (.deleteTopics admin-client topics)))

(defn re-delete-topics
  "Takes an instance of java.util.regex.Pattern and deletes all Kafka
  topics that match."
  [client-config re]
  (with-open [client (kafka/make-admin-client client-config)]
    (let [topics-to-delete (->> (kafka/list-topics client)
                             (filter #(re-find re %)))]
      (log/info "Deleting topics:" (map :topic-name topics-to-delete))
      (delete-topics! client topics-to-delete))))
