(ns kit.kit-test.web.async-render
  (:require
    [clojure.tools.logging :as log]
    [hiccup2.core :as h]
    [kit.kit-test.web.sse :as sse]))

(defn suspense [req {:keys [placeholder key sse-session]} delayed-content]
  (future
    (try
      (let [html (str (h/html @delayed-content))]
        (sse/send! req
          (sse/new-message sse-session key html)))
      (catch Exception e
        (log/error e "Error in delayed render"))))
  [:div {:sse-swap key}
   placeholder])
