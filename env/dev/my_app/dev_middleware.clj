(ns my-app.dev-middleware)

(defn wrap-dev [handler _opts]
  (-> handler))

