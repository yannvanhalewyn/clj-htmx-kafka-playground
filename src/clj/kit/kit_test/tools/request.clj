(ns kit.kit-test.tools.request)

(defn get? [req]
  (= (:request-method req) :get))

(defn patch? [req]
  (= (:request-method req) :patch))

(defn post? [req]
  (= (:request-method req) :post))
