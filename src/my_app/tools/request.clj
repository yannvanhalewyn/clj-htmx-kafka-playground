(ns my-app.tools.request)

(defn get? [req]
  (= (:request-method req) :get))

(defn patch? [req]
  (= (:request-method req) :patch))

(defn post? [req]
  (= (:request-method req) :post))

(defn wrap-assoc
  "Middleware for assoc'ing various keys onto requests"
  [handler k v]
  (fn [req]
    (handler (assoc req k v))))

(defn wrap-merge
  "Middleware for merging data onto request"
  [handler coll]
  (fn [req]
    (handler (merge req coll))))
