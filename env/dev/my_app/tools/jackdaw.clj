(ns my-app.tools.jackdaw
  (:require
    [clojure.tools.logging :as log]
    [jackdaw.admin :as ja]))

(defn re-delete-topics
  "Takes an instance of java.util.regex.Pattern and deletes all Kafka
  topics that match."
  [client-config re]
  (with-open [client (ja/->AdminClient client-config)]
    (let [topics-to-delete (->> (ja/list-topics client)
                             (filter #(re-find re (:topic-name %))))]
      (log/info "Deleting topics:" (map :topic-name topics-to-delete))
      (ja/delete-topics! client topics-to-delete))))
