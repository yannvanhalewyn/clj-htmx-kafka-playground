(ns kit.kit-test.env
  (:require [clojure.tools.logging :as log]))

(def defaults
  {:init       (fn []
                 (log/info "\n-=[kit-test starting]=-"))
   :start      (fn []
                 (log/info "\n-=[kit-test started successfully]=-"))
   :stop       (fn []
                 (log/info "\n-=[kit-test has shut down successfully]=-"))
   :middleware (fn [handler _] handler)
   :opts       {:profile :prod}})
