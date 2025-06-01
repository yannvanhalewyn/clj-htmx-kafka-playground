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
