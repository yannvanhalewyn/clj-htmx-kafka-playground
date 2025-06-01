(ns my-app.env
  (:require [clojure.tools.logging :as log]))

(def defaults
  {:init       (fn []
                 (log/info "\n-=[my-app starting]=-"))
   :start      (fn []
                 (log/info "\n-=[my-app started successfully]=-"))
   :stop       (fn []
                 (log/info "\n-=[my-app has shut down successfully]=-"))
   :middleware (fn [handler _] handler)
   :opts       {:profile :prod}})
