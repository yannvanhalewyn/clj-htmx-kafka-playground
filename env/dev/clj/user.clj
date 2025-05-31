(ns user
  "Userspace functions you can run by default in your local REPL."
  (:require
    [hashp.preload]
    [sc.api]
    [clojure.pprint]
    [clojure.spec.alpha :as s]
    [clojure.tools.namespace.repl :as repl]
    [expound.alpha :as expound]
    [integrant.core :as ig]
    [integrant.repl :as ir]
    [integrant.repl.state :refer [system]]
    [kit.api :as kit]
    [kit.kit-test.config :as config]
    [kit.kit-test.core]
    [kit.kit-test.web.sse :as sse]
    [lambdaisland.classpath.watch-deps :as watch-deps]
    [portal.api :as portal]
    [xtdb.api :as xt]))

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

(defonce portal-instance (atom nil))

(defn start-portal! []
  (add-tap #'portal/submit)
  (reset! portal-instance
    (portal/open
      {:app false
       :port 60342})))

(defn db-node []
  (:kit.kit-test.db/db-node system))

(defn db []
  (xt/db (db-node)))

(defn sse-listener []
  (-> system :kit.kit-test.web.sse/sse-listener))

(comment
  (watch-deps/start! {:aliases [:dev :test]})
  (ir/go)
  (ir/reset)
  (ir/halt)
  (repl/refresh-all)
  (keys system)
  (sse/send! (sse-listener)
    {:sse/event "message"
     :sse/data nil
     :sse/topic "delayed-message-test"})

  (xt/entity-history (db)
    :person
    :asc
    {:with-docs? true
     :start-valid-time #inst "2025-05-31T09:25:40.000-00:00"})


  ;; Install KIT module
  (kit/sync-modules)
  (kit/list-modules)
  (kit/install-module :kit/htmx)
  (kit/install-module :kit/simpleui)
  (kit/install-module :kit/tailwind)
  (kit/install-module :kit/xtdb))

(require
  '[puget.color.ansi :as color]
  '[puget.printer :as puget]
  '[clojure.tools.logging :as log])

(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(alter-var-root #'hashp.preload/print-opts assoc :namespace-maps false)
(alter-var-root #'hashp.preload/print-log
  (constantly
    (fn print-log [trace form value]
      (locking hashp.preload/lock
        (log/debug
          (str
            (color/sgr "#p" :red) (color/sgr (hashp.preload/trace-str trace) :green) "\n\n"
            ;;(str (puget/pprint-str form hashp.preload/no-color-print-opts) "\n\n")
            (puget/pprint-str value hashp.preload/print-opts)
            "\n"))))))

; don't use namespaced maps by default in the REPL
(defmethod print-method clojure.lang.IPersistentMap [m, ^java.io.Writer w]
  (#'clojure.core/print-meta m w)
  (#'clojure.core/print-map m #'clojure.core/pr-on w))
