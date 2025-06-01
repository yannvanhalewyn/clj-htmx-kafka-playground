(ns kit.kit-test.tools.utils)

(defn remove-vals
  "Returns a map without entries for which predicate returns true"
  [pred coll]
  (into {} (remove (comp pred val) coll)))

(defn prune
  "Removes any nil values from a map"
  [coll]
  (remove-vals nil? coll))
