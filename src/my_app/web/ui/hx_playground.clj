(ns my-app.web.ui.hx-playground
  (:require
    [my-app.tools.ui :as ui]
    [my-app.web.ui.layout :as layout]
    [simpleui.core :as su]))

(def info
  {:path "hx-playground"
   :emoji "🎈"
   :title "HTMX Playground"
   :blurb "Type and watch the server answer on every keystroke."
   :intro "No JavaScript written, no JSON parsed. Each input sends a request
           and the server replies with a tiny piece of HTML that HTMX drops
           into the page."
   :attrs ["hx-patch" "hx-trigger" "hx-target" "hx-swap"]
   :steps
   [(list "Typing fires an " (ui/code "input") " event. "
          (ui/code "hx-trigger=\"input changed delay:100ms\"")
          " waits until you pause for 100ms, and skips the request if the
          value didn't change.")
    (list "HTMX sends " (ui/code "PATCH /hello") " with the input's "
          (ui/code "name") " and value as form params. SimpleUI maps "
          (ui/code "my-name") " onto the component's argument.")
    (list "The server renders the " (ui/code "hello") " component and HTMX
          replaces " (ui/code "#hello") " with it, because of "
          (ui/code "hx-swap=\"outerHTML\"") ".")]})

(su/defcomponent ^:endpoint hello [_req my-name]
  [:div#hello.min-h-6
   (when (seq my-name)
     [:span.text-lg "👋 Hello, " [:strong.text-sun my-name] "!"])])

(su/defcomponent ^:endpoint cart [_req cart-msg]
  [:div#cart.min-h-6
   (when (seq cart-msg)
     [:span.text-lavender "🛒 The server heard: "
      [:span.italic.text-white cart-msg]])])

(su/defcomponent ^:endpoint hx-playground [req]
  hello
  cart
  (layout/if-page-load req
    (layout/demo (assoc info :code [#'hello #'cart #'hx-playground])
      [:div.space-y-8
       ;; Greeter
       [:div
        [:label.label {:for "my-name"} "What's your name?"]
        [:input.input
         {:type "text"
          :id "my-name"
          :name "my-name"
          :placeholder "Ada Lovelace"
          :autocomplete "off"
          :hx-patch "hello"
          :hx-trigger "input changed delay:100ms"
          :hx-target "#hello"
          :hx-swap "outerHTML"}]
        [:div.mt-3
         (hello req nil)]]

       ;; Message echo
       [:div
        [:label.label {:for "cart-msg"} "Tell the server something"]
        [:textarea.input
         {:id "cart-msg"
          :name "cart-msg"
          :rows 3
          :placeholder "Two croissants, please"
          :hx-patch "cart"
          :hx-trigger "input changed delay:100ms"
          :hx-target "#cart"
          :hx-swap "outerHTML"}]
        [:div.mt-3
         (cart req nil)]]])))
