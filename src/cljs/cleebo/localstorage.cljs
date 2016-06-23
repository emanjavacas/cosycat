(ns cleebo.localstorage
  (:require [re-frame.core :as re-frame]
            [cleebo.utils :refer [keywordify]]
            [taoensso.timbre :as timbre]
            [cleebo.schemas.app-state-schemas :refer [db-schema]]
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

(defn delete
  "Remove the browser's localStorage value for the given `key`"
  [key]
  (.removeItem (.-localStorage js/window) key))

(defn recover-db
  [k & {:keys [path] :or {path [:db]}}]
  (let [coercion-fn (coerce-json path)]
    (try
      (let [db (fetch k)
            parsed-db (coercion-fn db)]
        (assoc-in parsed-db [:session :notifications] {}))
      (catch :default e
        (timbre/debug "Couldn't coerce DB")))))

(defn recover-all-db-keys []
  (let [ks (js->clj (.keys js/Object js/localStorage))]
    (reverse (sort-by #(.parse js/Date %) ks))))

(defn dump-db [& {:keys [max-dbs] :or {max-dbs 10}}]
  (let [dbs (recover-all-db-keys)]
    (when (> (count dbs) max-dbs)
      (doseq [k (drop 9 dbs)]
        (delete k)))
    (re-frame/dispatch [:dump-db])))
