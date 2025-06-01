(ns kit.kit-test.web.ui.delayed-render-sse
  (:require
    [clojure.core.async :as a]
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

(defn- get-events []
  (for [i (map inc (range 20))]
    {:name (str "Event " i)}))

(defn- section [title & children]
  (into
    [:div.mt-4.space-y-4
     [:h2.heading-2 title]]
    children))

(su/defcomponent ^:endpoint delayed-render [req]
  (let [sse-session (sse/new-session SSE_TOPIC_NAME)]
    (layout/if-page-load req
      (sse/hx-listener sse-session
        (section "Users"
          (async-render/suspense req
            {:sse-session sse-session
             :key "users"
             :placeholder [:p "Loading Users..."]}
            (future
              (Thread/sleep 5000)
              [:div.animate-fade-in
               (for [user (get-users)]
                 [:p (:name user) " "
                  [:span.italic  "(" (:email user) ")"]])])))

        (section "Comments"
          (async-render/suspense req
            {:sse-session sse-session
             :key "comments"
             :placeholder [:p "Loading Comments..."]}
            (future
              (Thread/sleep 2000)
              [:div.animate-fade-in
               (for [c (get-comments)]
                 [:p (:comment c)])])))

        (section "Streamed events"
          (async-render/stream req
            {:sse-session sse-session
             :key "streamed-events"
             :hx-swap "beforeend"}
            (fn [hiccup-ch done-fn]
              (a/go-loop [[cur & others] (get-events)]
                (a/<! (a/timeout 500))
                (if cur
                  ;; Returns nil when channel is closed
                  ;; A closed channel means the client left.
                  (when (a/>! hiccup-ch [:p (:name cur)])
                    (recur others))
                  (do
                    (a/>! hiccup-ch [:p "No more events..."])
                    (done-fn)))))))))))

(comment
  (defn replace-body [msg]
    (sse/send! (user/sse-listener)
      {:sse/event "comments"
       :sse/data (str (h/html [:p msg]))
       :sse/topic SSE_TOPIC_NAME
       ;; Get session from browser connection or logs
       :sse/session-id #uuid "e73961a4-1cb1-4001-a464-c4dff32df1a5"}))
  (replace-body "Hello!")
  (replace-body "Replace async"))
