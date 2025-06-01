(ns kit.kit-test.web.ui.delayed-render-sse
  (:require
    [hiccup2.core :as h]
    [kit.kit-test.web.async-render :as async-render]
    [kit.kit-test.web.sse :as sse]
    [kit.kit-test.web.ui.layout :as layout]
    [simpleui.core :as su]))

(def SSE_TOPIC_NAME "delayed-render-component")

(defn- get-users []
  (for [i (map inc (range 5))]
    {:name (str "User " i)
     :email (str "user" i "@example.com")}))

(defn- get-comments []
  (for [i (map inc (range 10))]
    {:comment (str "Comment " i)}))

(defn- section [title & children]
  (into
    [:div.mt-4.space-y-4
     [:h2.heading-2 title]]
    children))

(su/defcomponent ^:endpoint delayed-render [req]
  (layout/if-page-load req
    (sse/hx-listener
      {:sse/topic SSE_TOPIC_NAME}

      (section "Users"
        (async-render/suspense req
          {:sse/topic SSE_TOPIC_NAME
           :key "delayed-users"
           :placeholder [:p "Loading Users..."]}
          (future
            (Thread/sleep 5000)
            [:div.animate-fade-in
             (for [user (get-users)]
               [:p (:name user) " "
                [:span.italic  "(" (:email user) ")"]])])))

      (section "Comments"
        (async-render/suspense req
          {:sse/topic SSE_TOPIC_NAME
           :key "delayed-comments"
           :placeholder [:p "Loading Comments..."]}
          (future
            (Thread/sleep 2000)
            [:div.animate-fade-in
             (for [c (get-comments)]
               [:p (:comment c)])]))))))

(comment
  (defn replace-body [msg]
    (sse/send! (user/sse-listener)
      {:sse/event "delayed-comments"
       :sse/data (str (h/html [:p msg]))
       :sse/topic SSE_TOPIC_NAME}))
  (replace-body "Hello!")
  (replace-body "Replace async")

  (require '[clojure.core.async :as a])

  (def stop-ch (a/chan))

  (a/go-loop [i 1]
    (replace-body (str "Count: " i))
    (let [[msg ch] (a/alts!! [stop-ch (a/timeout 1000)])]
      (if (= ch stop-ch)
        (replace-body msg)
        (recur (inc i)))))

  (a/put! stop-ch "Stop!"))
