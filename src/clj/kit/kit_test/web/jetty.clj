(ns kit.kit-test.web.jetty
  (:require
    [clojure.tools.logging :as log]
    [integrant.core :as ig]
    [ring.adapter.jetty :as jetty])
  (:import
    (org.eclipse.jetty.server Server)))

(defmethod ig/init-key :server/http
  [_ {:keys [port handler]}]
  (log/info "starting HTTP server on port" port)
  (try
    (jetty/run-jetty handler
      {:port port
       :join? false
       :async? false})
    (catch Throwable t
      (log/error t (str "server failed to start on port" port))
      (throw t))))

(defmethod ig/halt-key! :server/http
  [_ ^Server server]
  (let [result (.stop server)]
    (log/info "HTTP server stopped")
    result))
