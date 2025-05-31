(ns kit.kit-test.web.async-render
  (:require
    [clojure.tools.logging :as log]
    [hiccup2.core :as h]
    [kit.kit-test.web.sse :as sse]))

(defn render-async! [req sse-event delayed-content]
  (future
    (try
      (sc.api/spy :async)
      (comment
        (sc.api/letsc 3 sse-event)
        (sc.api/letsc 3 (str (h/html @delayed-content)))
        (sc.api/letsc 3
          (assoc sse-event
            :sse/data (str (h/html @delayed-content)))))
      (sse/send! req
        (assoc sse-event
          :sse/data (str (h/html @delayed-content))))
      (catch Exception e
        (log/error e "Error in delayed render")))))

(defn suspense [req {:keys [placeholder key :sse/topic]} delayed-content]
  (render-async! req
    {:sse/topic topic
     :sse/event key}
    delayed-content)
  [:div {:sse-swap key}
   placeholder])

