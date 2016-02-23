(ns cleebo.localstorage
  (:require [cleebo.utils :refer [keywordify]]
            [taoensso.timbre :as timbre]
            [cleebo.backend.middleware :refer [db-schema]]
            [schema.core :as s]
            [schema.coerce :as coerce]))

(defn coerce-json [path]
  (let [schema (get-in {:db db-schema} path)]
    (coerce/coercer schema coerce/json-coercion-matcher)))

(defn put
  "Set `key' in browser's localStorage to `val`."
  [key val]
  (let [json-val (js/JSON.stringify (clj->js val))]
    (.log js/console "Dumped " key " to LocalStorage")
    (.setItem (.-localStorage js/window) key json-val)))

(defn fetch
  "Returns value of `key' from browser's localStorage."
  [key]
  (if-let [val (.getItem (.-localStorage js/window) key)]
    (-> val
        js/JSON.parse
        js->clj
        keywordify)))

(defn recover-db
  [& {:keys [path] :or {path [:db]}}]
  (let [coercion-fn (coerce-json path)]
    (try
      (if-let [db (fetch :db)]
        (do (.log js/console (coercion-fn db))
            (coercion-fn db)))
      (catch :default e
        (timbre/debug "Couldn't coerce DB")
        nil))))

(defn delete
  "Remove the browser's localStorage value for the given `key`"
  [key]
  (.removeItem (.-localStorage js/window) key))

