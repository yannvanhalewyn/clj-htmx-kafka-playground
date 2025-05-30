(ns kit.kit-test.web.views.hello
  (:require
    [kit.kit-test.web.htmx :as htmx]
    [simpleui.core :as simpleui :refer [defcomponent]]))

(defcomponent ^:endpoint hello [req my-name]
  [:div#hello "Hello " my-name])

(defcomponent ^:endpoint cart [req items]
  [:div#cart "Cart: " items])

(defn ui-routes [base-path]
  (simpleui/make-routes
    (str base-path "/ui/")
    (fn [req]
      (htmx/page
        [:label {:style "margin-right: 10px"}
         "What is your name?"]
        [:input {:type "text"
                 :name "my-name"
                 :hx-patch "hello"
                 :hx-target "#hello"
                 :hx-swap "outerHTML"}]
        (hello req "")
        (cart req [{:id "1" :name "Apple"}])))))
