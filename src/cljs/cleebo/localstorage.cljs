(ns cleebo.localstorage
  (:require [re-frame.core :as re-frame]
            [schema.core :as s]
            [schema.coerce :as coerce]
            [cleebo.utils :refer [keywordify]]
            [cleebo.schemas.app-state-schemas :refer [db-schema]]
            [cleebo.app-utils :refer [dissoc-in]]
            [taoensso.timbre :as timbre]))

(defn coerce-json
  "attempts to coerce a json object into the db schema specified by `path`"
  [path]
  (let [schema (get-in {:db db-schema} path)]
    (coerce/coercer schema coerce/json-coercion-matcher)))

(defn put
  "set `key' in browser's localStorage to `val`."
  [key val]
  (let [json-val (js/JSON.stringify (clj->js val))]
    (.setItem (.-localStorage js/window) key json-val)))

(defn fetch
  "returns value of `key' from browser's localStorage."
  [key & {:keys [parse] :or {parse true}}]
  (-> (.getItem (.-localStorage js/window) key) js/JSON.parse js->clj))

(defn delete
  "remove the browser's localStorage value for the given `key`"
  [key]
  (.removeItem (.-localStorage js/window) key))

(defn delete-in
  "deletes a nested object from localstorage"
  [path]
  (let [[key & rest] path]
    (if-let [val (fetch key)]
      (put key (dissoc-in val rest)))))

(defn store-db
  "stores input db inside [:db date]"
  [db]
  (put :db {(.now js/Date) db}))

(defn fetch-dbs
  "returns all stored db from localstorage in a parsed form"
  [& {:keys [path] :or {path [:db]}}]
  (let [coercion-fn (coerce-json path)]
    (-> (fetch :db) keywordify)))

(defn fetch-db
  "fetches a single db given its backup timestamp"
  [backup & {:keys [path] :or {path [:db]}}]
  (let [coercion-fn (coerce-json path)]
    (try
      (let [parsed-db (-> (fetch :db) (get backup) keywordify coercion-fn)]
        (assoc-in parsed-db [:session :notifications] {}))
      (catch :default e
        (timbre/error "Couldn't coerce DB")))))

(defn get-backups
  "fetches all backup timestamps in sorted order (youngest first)"
  []
  (let [backups (-> (fetch :db) keys)]
    (reverse (sort-by #(.parse js/Date %) backups))))

(defn fetch-last-dump
  "fetches last stored db dump"
  []
  (-> (get-backups) first fetch-db))

(defn dump-db
  "calls dump-db event after cleaning up localstorage to a max dbs of `max-dbs`"
  [& {:keys [max-dbs] :or {max-dbs 10}}]
  (let [backups (get-backups)]
    (when (> (count backups) max-dbs)
      (doseq [backup (drop (dec max-dbs) backups)]
        (delete-in [:db backup])))
    (re-frame/dispatch [:dump-db])))

(defn with-ls-cache
  "variadic function that can be use to retrieve and store the results of a computation
  in localstorage. Needs a unique key from function input params. Returns the computation
  as such after being stored"
  ([key] (fetch key))
  ([key val] (do (put key val) val)))

