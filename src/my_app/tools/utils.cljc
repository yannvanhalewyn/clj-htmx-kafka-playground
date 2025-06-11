(ns my-app.tools.utils)

(defn remove-vals
  "Returns a map without entries for which predicate returns true"
  [pred coll]
  (into {} (remove (comp pred val) coll)))

(defn prune
  "Removes any nil values from a map"
  [coll]
  (remove-vals nil? coll))

(defn dissoc-in
  "Dissoc's the element at path in coll"
  [coll path]
  (if (= 1 (count path))
    (dissoc coll (first path))
    (update-in coll (butlast path) dissoc (last path))))

(defn assoc-some
  "assoc key/value pairs to the map only on non-nil values

   (assoc-some {} :a 1)
   => {:a 1}

   (assoc-some {} :a 1 :b nil)
   => {:a 1}"
  ([m k v]
   (if (some? v) (assoc m k v) m))
  ([m k v & more]
   (apply assoc-some (assoc-some m k v) more)))

