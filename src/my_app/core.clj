(ns my-app.core
  (:require
    [clojure.tools.logging :as log]
    [integrant.core :as ig]
    [my-app.config :as config]
    [my-app.db]
    [my-app.env :refer [defaults]] ;
    [my-app.events]
    [my-app.web.api.routes]
    [my-app.web.handler]
    [my-app.web.jetty]
    [my-app.web.ui.routes])
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
