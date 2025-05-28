(ns kit.kit-test.env
  (:require
    [clojure.tools.logging :as log]
    [kit.kit-test.dev-middleware :refer [wrap-dev]]))

(def defaults
  {:init       (fn []
                 (log/info "\n-=[kit-test starting using the development or test profile]=-"))
   :start      (fn []
                 (log/info "\n-=[kit-test started successfully using the development or test profile]=-"))
   :stop       (fn []
                 (log/info "\n-=[kit-test has shut down successfully]=-"))
   :middleware wrap-dev
   :opts       {:profile       :dev}})
