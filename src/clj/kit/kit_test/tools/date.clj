(ns kit.kit-test.tools.date)

(defn format-iso [date]
  (let [date-formatter (java.text.SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")]
    (.format date-formatter date)))

(defn format-date [date]
  (let [date-formatter (java.text.SimpleDateFormat. "dd MM Y 'at' HH:mm")]
    (.format date-formatter date)))

(do
  (format-date (java.util.Date.)))
