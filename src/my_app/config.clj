(ns my-app.config
  (:require
    [aero.core :as aero]
    [clojure.java.io :as io]
    [clojure.tools.logging :as log]
    [integrant.core :as ig]))

(defmethod aero/reader 'ig/ref
  [_ _ value]
  (ig/ref value))

(defn read-config
  [filename options]
  (log/info "Reading config" filename)
  (aero/read-config (io/resource filename) options))

(defmethod ig/init-key :system/env [_ env] env)

(def ^:const system-filename "system.edn")

(defn system-config
  [options]
  (read-config system-filename options))


(comment
  (def config- (read-config system-filename {:profile :production}))

  (tap> config-)
  (seq (:my-app.archive/archiving-pipeline config-)))
