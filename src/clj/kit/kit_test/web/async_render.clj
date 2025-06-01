(ns kit.kit-test.web.async-render
  (:require
    [clojure.tools.logging :as log]
    [hiccup2.core :as h]
    [kit.kit-test.web.sse :as sse]))

(defn suspense [req {:keys [placeholder key :sse/topic]} delayed-content]
  (future
    (try
      (sse/send! req
        {:sse/event key
         :sse/topic topic
         :sse/data (str (h/html @delayed-content))})
      (catch Exception e
        (log/error e "Error in delayed render"))))
  [:div {:sse-swap key}
   placeholder])
