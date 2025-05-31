(ns kit.kit-test.db
  (:require
   [clojure.java.io :as io]
   [integrant.core :as ig]
   [xtdb.api :as xt]))

(defn kv-store
  [dir]
  {:kv-store {:xtdb/module 'xtdb.rocksdb/->kv-store
              :db-dir (io/file dir)
              :sync? true}})

(defn start-db!
  [{:keys [tx-log document-store index-store]}]
  (xt/start-node
    {:xtdb/tx-log (kv-store tx-log)
     :xtdb/document-store (kv-store document-store)
     :xtdb/index-store (kv-store index-store)}))

(defmethod ig/init-key ::db-node
  [_ opts]
  (start-db! opts))

(defmethod ig/halt-key! ::db-node
  [_ db-node]
  (.close db-node))
