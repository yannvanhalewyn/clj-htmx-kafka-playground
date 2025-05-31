(ns kit.kit-test.tools.ui)

(defn hidden [name val]
  [:input.input
   {:type "hidden"
    :id name
    :name name
    :value val}])

(defn text [name val]
  [:input.input
   {:type "text"
    :id name
    :name name
    :value val}])

(defn email [name val]
  [:input.input
   {:type "email"
    :id name
    :name name
    :value val}])

(defn link [url body]
  [:a.text-link
   {:href url
    :hx-get url
    :hx-target "#app-root"
    :hx-push-url "true"}
   body])
