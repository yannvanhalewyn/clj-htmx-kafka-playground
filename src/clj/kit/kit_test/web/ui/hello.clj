(ns kit.kit-test.web.ui.hello
  (:require
    [kit.kit-test.web.htmx :as htmx]
    [simpleui.core :as simpleui :refer [defcomponent]]))

(defcomponent ^:endpoint hello [req]
  [:div#hello "Hello "])

(defcomponent ^:endpoint cart [req]
  [:div#cart "Cart: "])

(defn ui-routes [base-path]
  (simpleui/make-routes
    base-path
    (fn [req]
      (htmx/page
        [:label {:style "margin-right: 10px"}
         "What is your name?"]
        [:input {:type "text"
                 :name "my-name"
                 :hx-patch "hello"
                 :hx-target "#hello"
                 :hx-swap "outerHTML"}]
        (hello req)
        (cart req)))))

(comment
  (ui-routes "/ui/"))
