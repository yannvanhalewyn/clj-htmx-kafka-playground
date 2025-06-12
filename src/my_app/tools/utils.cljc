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

(defn filter-keys
  "Returns a map with only those entries for which (f val) returns true"
  [f coll]
  (into {} (filter (fn [[k _]] (f k)) coll)))

(defn build-index
  "Reduces a coll into a map as follows:
    {(key-fn el) (val-fn el)}

  When no val-fn is supplied will create a hashmap with the elements keyed to
  their key-fn:
    {(key-fn el) el}

  @example
  (def users
    [{:name \"John\" :id 2} {:name \"Jeff\" :id 3}])

  (build-index :id users)
  ;; => {2 {:name \"John\" :id 2}
         3 {:name \"Jeff\" :id 3}}

  (build-index :id :name users)
  ;; => {2 \"John\"
         3 \"Jeff\"}

  Useful when building an index out of a list. Using this prevents combining
  `key-by` with `map-vals`, or to replace `(into {})`."
  ([key-fn coll]
   (build-index key-fn identity coll))
  ([key-fn val-fn coll]
   (persistent!
     (reduce #(assoc! %1 (key-fn %2) (val-fn %2)) (transient {}) coll))))
