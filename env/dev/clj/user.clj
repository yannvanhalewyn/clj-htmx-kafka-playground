(ns user
  "Userspace functions you can run by default in your local REPL."
  (:require
    [clojure.pprint]
    [clojure.spec.alpha :as s]
    [clojure.tools.namespace.repl :as repl] ;; benchmarking
    [expound.alpha :as expound]
    [integrant.core :as ig]
    [integrant.repl :as ir]
    [kit.api :as kit]
    [kit.kit-test.config :as config]
    [kit.kit-test.web.api.routes]
    [kit.kit-test.web.handler]
    [kit.kit-test.web.ui.routes]
    [lambdaisland.classpath.watch-deps :as watch-deps])) ;; hot loading for deps

(defn dev-prep!
  []
  (ir/set-prep!
    (fn []
      (ig/expand
        (config/system-config {:profile :dev})))))

(defn test-prep!
  []
  (integrant.repl/set-prep!
    (fn []
      (ig/expand
        (config/system-config {:profile :test})))))

;; uncomment to enable hot loading for deps
(alter-var-root #'s/*explain-out* (constantly expound/printer))
(add-tap (bound-fn* clojure.pprint/pprint))

(dev-prep!)
(repl/set-refresh-dirs "src/clj" "env/dev/")

(comment
  (watch-deps/start! {:aliases [:dev :test]})
  (ir/go)
  (ir/reset)
  (repl/refresh)
  (repl/refresh-all)

  ;; Install KIT module
  (kit/list-modules)
  (kit/install-module :kit/htmx)
  (kit/install-module :kit/simpleui)
  (kit/install-module :kit/tailwind))
