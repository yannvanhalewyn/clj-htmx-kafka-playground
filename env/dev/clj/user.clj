(ns user
  "Userspace functions you can run by default in your local REPL."
  (:require
    [clojure.java.io :as io]
    [clojure.pprint]
    [clojure.spec.alpha :as s]
    [clojure.tools.namespace.repl :as repl] ;; benchmarking
    [expound.alpha :as expound]
    [integrant.core :as ig]
    [integrant.repl :as ir]
    [integrant.repl.state :refer [system]]
    [kit.api :as kit]
    [kit.kit-test.config :as config]
    [kit.kit-test.core]
    [lambdaisland.classpath.watch-deps :as watch-deps]
    [xtdb.api :as xt])) ;; hot loading for deps

(defn dev-prep!
  []
  (ir/set-prep!
    (fn []
      (ig/expand
        (config/system-config {:profile :dev})))))

(defn test-prep!
  []
  (integrant.repl/set-prep!
    (fn []
      (ig/expand
        (config/system-config {:profile :test})))))

;; uncomment to enable hot loading for deps
(alter-var-root #'s/*explain-out* (constantly expound/printer))
(add-tap (bound-fn* clojure.pprint/pprint))

(dev-prep!)
(repl/set-refresh-dirs "src/clj" "env/dev/")

(defn db-node []
  (:kit.kit-test.db/db-node system))

(defn db []
  (xt/db (db-node)))

(comment
  (watch-deps/start! {:aliases [:dev :test]})
  (ir/go)
  (ir/reset)
  (ir/halt)
  (repl/refresh)
  (repl/refresh-all)
  (keys system)

  ;; Install KIT module
  (kit/sync-modules)
  (kit/list-modules)
  (kit/install-module :kit/htmx)
  (kit/install-module :kit/simpleui)
  (kit/install-module :kit/tailwind)
  (kit/install-module :kit/xtdb))
