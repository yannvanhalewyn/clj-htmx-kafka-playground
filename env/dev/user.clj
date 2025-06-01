(ns user
  "Userspace functions you can run by default in your local REPL."
  (:require
    [clojure.pprint]
    [clojure.spec.alpha :as s]
    [clojure.tools.namespace.repl :as repl]
    [expound.alpha :as expound]
    [hashp.preload]
    [integrant.core :as ig]
    [integrant.repl :as ir]
    [integrant.repl.state :refer [system]]
    [lambdaisland.classpath.watch-deps :as watch-deps]
    [my-app.config :as config]
    [my-app.core]
    [portal.api :as portal]
    [sc.api]
    [xtdb.api :as xt]))

(set! *warn-on-reflection* true)

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
(repl/set-refresh-dirs "src" "env/dev")

(defonce portal-instance (atom nil))

(defn start-portal! []
  (add-tap #'portal/submit)
  (reset! portal-instance
    (portal/open
      {:app false
       :port 60342})))

(defn db-node []
  (:my-app.db/db-node system))

(defn db []
  (xt/db (db-node)))

(defn sse-listener []
  (-> system :my-app.web.sse/sse-listener))

(comment
  (watch-deps/start! {:aliases [:dev :test]})
  (start-portal!)
  (ir/go)
  (ir/reset)
  (ir/halt)
  (repl/refresh-all)
  (keys system)

  (xt/entity-history (db)
    :person
    :asc
    {:with-docs? true
     :start-valid-time #inst "2025-05-31T09:25:40.000-00:00"}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(comment
  (do
    (require 'hashp.preload)
    (require
      '[clojure.tools.logging :as log]
      '[puget.color.ansi :as color]
      '[puget.printer :as puget])


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
      (#'clojure.core/print-map m #'clojure.core/pr-on w))))
