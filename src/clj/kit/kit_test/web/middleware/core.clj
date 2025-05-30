(ns kit.kit-test.web.middleware.core
  (:require
    [kit.kit-test.env :as env]
    [ring.middleware.defaults :as defaults]
    [ring.middleware.session.cookie :as cookie]))

(defn wrap-base
  [{:keys [metrics site-defaults-config cookie-secret] :as opts}]
  (let [cookie-store (cookie/cookie-store {:key (.getBytes ^String cookie-secret)})]
    (fn [handler]
      (-> ((:middleware env/defaults) handler opts)
        (defaults/wrap-defaults
          (assoc-in site-defaults-config [:session :store] cookie-store))))))
