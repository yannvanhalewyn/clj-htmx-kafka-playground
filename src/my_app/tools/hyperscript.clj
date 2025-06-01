(ns my-app.tools.hyperscript)

(defn keydown [keyname]
  (format "keydown[key is '%s']" keyname))

(defn on
  ([event handler]
   (format "on %s %s" event handler))
  ([event from handler]
   (format "on %s from %s %s" event from handler)))

