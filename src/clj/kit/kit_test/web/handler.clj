(ns kit.kit-test.web.handler
  (:require
    [integrant.core :as ig]
    [kit.kit-test.web.middleware.core :as middleware]
    [reitit.ring :as ring]
    [reitit.swagger-ui :as swagger-ui]
    [ring.util.http-response :as ring.response]))

(defmethod ig/init-key ::ring-handler
  [_ {:keys [router api-path] :as opts}]
  (ring/ring-handler
    router
    (ring/routes
      ;; Handle trailing slash in routes - add it + redirect to it
      ;; https://github.com/metosin/reitit/blob/master/doc/ring/slash_handler.md
      (ring/redirect-trailing-slash-handler)
      (ring/create-resource-handler {:path "/"})
      (when (some? api-path)
        (swagger-ui/create-swagger-ui-handler {:path api-path
                                               :url  (str api-path "/swagger.json")}))
      (ring/create-default-handler
        {:not-found
         (constantly (-> {:status 404, :body "Page not found"}
                       #_{:clj-kondo/ignore [:unresolved-var]}
                       (ring.response/content-type "text/plain")))
         :method-not-allowed
         (constantly (-> {:status 405, :body "Not allowed"}
                       #_{:clj-kondo/ignore [:unresolved-var]}
                       (ring.response/content-type "text/plain")))
         :not-acceptable
         (constantly (-> {:status 406, :body "Not acceptable"}
                       #_{:clj-kondo/ignore [:unresolved-var]}
                       (ring.response/content-type "text/plain")))}))
    {:middleware [(middleware/wrap-base opts)]}))

(defmethod ig/init-key ::ring-router
  [_ {:keys [routes] :as opts}]
  (ring/router routes))
