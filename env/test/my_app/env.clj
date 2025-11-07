(ns my-app.env
  (:require
    [clojure.tools.logging :as log]
    [my-app.dev-middleware :refer [wrap-dev]]))

(def defaults
  {:init       (fn []
                 (log/info "\n-=[my-app starting using the development or test profile]=-"))
   :start      (fn []
                 (log/info "\n-=[my-app started successfully using the development or test profile]=-"))
   :stop       (fn []
                 (log/info "\n-=[my-app has shut down successfully]=-"))
   :middleware wrap-dev
   :opts       {:profile       :dev}})
