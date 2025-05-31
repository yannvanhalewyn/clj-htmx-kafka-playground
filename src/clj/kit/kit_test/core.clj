(ns kit.kit-test.core
  (:require
    [clojure.tools.logging :as log]
    [integrant.core :as ig]
    [kit.kit-test.config :as config]
    [kit.kit-test.db]
    [kit.kit-test.env :refer [defaults]] ;
    [kit.kit-test.web.api.routes]
    [kit.kit-test.web.handler]
    [kit.kit-test.web.jetty]
    [kit.kit-test.web.ui.routes])
  (:gen-class))

;; log uncaught exceptions in threads
(Thread/setDefaultUncaughtExceptionHandler
  (fn [^Thread thread ex]
    (log/error {:what :uncaught-exception
                :exception ex
                :where (str "Uncaught exception on" (.getName thread))})))

(defonce system (atom nil))

(defn stop-app []
  ((or (:stop defaults) (fn [])))
  (some-> (deref system) (ig/halt!)))

(defn start-app [& [params]]
  ((or (:start params) (:start defaults) (fn [])))
  (->> (config/system-config (or (:opts params) (:opts defaults) {}))
    (ig/expand)
    (ig/init)
    (reset! system)))

(defn -main [& _]
  (start-app)
  (.addShutdownHook (Runtime/getRuntime) (Thread. (fn [] (stop-app) (shutdown-agents)))))
