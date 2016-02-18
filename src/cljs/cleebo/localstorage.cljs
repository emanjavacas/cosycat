(ns cleebo.localstorage
  (:require [cleebo.utils :refer [keywordify coerce-json]]))

(defn put!
  "Set `key' in browser's localStorage to `val`."
  [key val]
  (let [json-val (js/JSON.stringify (clj->js val))]
    (.log js/console "Dumped " key " to LocalStorage")
    (.setItem (.-localStorage js/window) key json-val)))

(defn fetch
  "Returns value of `key' from browser's localStorage."
  [key & {:keys [coercion-fn] :or {coercion-fn identity}}]
  (if-let [val (.getItem (.-localStorage js/window) key)]
    (-> val
        js/JSON.parse
        js->clj
        keywordify
        coercion-fn)))

(defn remove!
  "Remove the browser's localStorage value for the given `key`"
  [key]
  (.removeItem (.-localStorage js/window) key))

