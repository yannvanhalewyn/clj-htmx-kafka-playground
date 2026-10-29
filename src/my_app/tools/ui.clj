(ns my-app.tools.ui
  (:require
    [clojure.repl :as repl]
    [clojure.string :as str]))

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

(defn link
  ([url body]
   (link url {:class "text-link"} body))
  ([url attrs body]
   [:a
    (merge
      {:href url
       :hx-get url
       :hx-target "#app-root"
       :hx-push-url "true"}
      attrs)
    body]))

(defn code
  "Inline code snippet"
  [s]
  [:code.inline s])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Attribute chips

(defn- attr-docs-url [attr]
  (cond
    (str/starts-with? attr "hx-ext") "https://htmx.org/attributes/hx-ext/"
    (str/starts-with? attr "hx-") (str "https://htmx.org/attributes/" attr "/")
    (str/starts-with? attr "sse-") "https://htmx.org/extensions/sse/"
    (= attr "_") "https://hyperscript.org/"))

(defn attr-chip
  "A small pill naming an HTMX attribute. Links to its docs unless `:static?`"
  [attr & {:keys [static?]}]
  (if-let [url (and (not static?) (attr-docs-url attr))]
    [:a.chip {:href url :target "_blank" :rel "noopener"} attr]
    [:span.chip attr]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Show code

(defn- var-source [v]
  (or (repl/source-fn (symbol v))
      (str ";; Source for " (symbol v) " is not available")))

(defn show-code
  "A foldout that renders the source of the given vars, read straight from the
  classpath so the docs never drift from the code."
  [& vars]
  [:details.code-foldout.mt-8
   [:summary
    [:span.when-closed "Show code"]
    [:span.when-open "Hide code"]]
   (for [v vars
         :let [{:keys [file line]} (meta v)]]
     [:div.code-block
      [:div.code-block__header
       [:span.text-lavender (str (symbol v))]
       [:span (str file ":" line)]]
      [:pre [:code.language-clojure (var-source v)]]])])
