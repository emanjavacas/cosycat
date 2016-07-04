(ns cleebo.vcs
  (:refer-clojure :exclude [sort find update])
  (:require [cleebo.components.db :refer [new-db]]
            [monger.collection :as mc]
            [monger.query :refer :all]
            [monger.operators :refer :all])
  (:import org.bson.types.ObjectId))

(def ^:dynamic *hist-coll-name* "_vcs")

(defn new-vcs-coll-name [coll-name]
  (str coll-name *hist-coll-name*))

(defn new-uuid []
  (str (java.util.UUID/randomUUID))
  ;; (ObjectId.) ;or just use mongodb's id
  )

;;; find operations:
;;; find-*
(defn merge-history
  "construct the output vcs doc"
  [doc vcs-history merge-field]
  (assoc doc merge-field vcs-history))

(defn find-history
  "retrieves document history from the backup collection"
  [db doc-id]
  (with-collection db *hist-coll-name*
    (find {:doc-id doc-id})
    (sort (array-map :_version (if reverse 1 -1)))))

(defn history
  "seq of version history maps sorted in version order"
  [db {:keys [_id] :as doc} & {:keys [reverse merge-docs] :or {reverse false merge-field :history}}]
  (if-let [vcs-history (find-history db _id)]
    (merge-history doc vcs-history merge-docs)))

;;; modify operations:
;;; insert; insert-*; remove; save; save-and-return; update; update-*; upsert
(defn ex-version
  "exception for not-matching version updates"
  [{last-vcs-version :_version} {new-doc-version :_version}]
  (ex-info "Claimed version not in sync with collection version"
           {:last-vcs-version last-vcs-version
            :claimed-version new-doc-version}))

(defn ex-id
  "exception for not-matching document ids"
  [id]
  (ex-info "Couldn't find document by id" {:id id}))

(defn update-version
  "increase version of a document in a vcs-collection"
  [db coll {:keys [_id]}]
  (mc/update db coll {:_id _id} {$inc {:_version 1}}))

(defn insert-new-version
  "inserts the last modified version of a document into the vcs collection"
  [db {:keys [_version] :as doc}
   & {:keys [merge-field] :as args :or {merge-field :history}}]
  {:pre [(and _version)]}
  (let [{vcs-history merge-field :as vcs-doc} (history db doc :reverse false)]
    (if-not (= _version (inc (:_version (first vcs-history))))
      (throw (ex-version (first vcs-history) doc))
      (mc/insert db *hist-coll-name* doc))))

(defn wrap-modify
  "function wrapper for native monger fns"
  [f id-getter version-setter]
  (fn wrapped-func [db coll & args]
    (let [id (apply id-getter db coll args)]
      (if-let [doc (mc/find-one-as-map db coll {:_id id})] ;1 search
        (insert-new-version db doc)     ;1 insert (& 1 search: for safety reasons)
        (throw (ex-id id))))
    (apply version-setter f db coll args)))

;;; id getters
(defn first-id-getter
  "id getter for fn signatures with doc/condition argument in first position (after db, coll).
   Fns: insert, insert-and-return, save, save-and-return, find-and-modify, remove, update, upsert"
  [db coll {:keys [_id] :as doc} & args]
  (assert _id "Couldn't find id in document")
  _id)

(defn by-id-getter
  "doc getter for fn signatures with id argument in first position (after db, coll).
   Fns: remove-by-id, update-by-id"
  [db coll id & args]
  id)

;;; version setters
(defn doc-version-setter
  "version setter for document inserts. Fns: insert, insert-and-return"
  [f db coll doc & args]
  (apply f db coll (assoc doc :_version 0) args))

(defn save-version-setter
  "version setter for ambiguous ops which might insert or update. Fns: save, save-and-return"
  [f db coll {:keys [_id] :as doc} & args]
  (assert _id "Couldn't find id in document")
  (apply f db coll doc args)
  (update-version db coll doc))

(defn update-version-setter
  "version setter for document updates with mongodb operators.
   Fns: update, update-by-id, upsert"
  [f db coll conditions-or-id doc & args]
  (apply f db coll conditions-or-id (assoc doc $inc {:_version 1}) args))

(defn no-version-setter
  "version setter for ops removing the document from db.
   Fns: remove, remove-by-id"
  [f db coll & args]
  (apply f db coll args))

;;; public wrapper api
(def find-and-modify (wrap-modify mc/find-and-modify first-id-getter update-version-setter))
(def insert (wrap-modify mc/insert-and-return first-id-getter doc-version-setter))
(def insert-and-return (wrap-modify mc/insert-and-return first-id-getter doc-version-setter))
(def remove (wrap-modify mc/remove first-id-getter no-version-setter))
(def remove-by-id (wrap-modify mc/remove-by-id by-id-getter no-version-setter))
(def save (wrap-modify mc/save first-id-getter save-version-setter))
(def save-and-return (wrap-modify mc/save-and-return first-id-getter save-version-setter))
(def update (wrap-modify mc/update first-id-getter update-version-setter))
(def update-by-id (wrap-modify mc/update-by-id by-id-getter update-version-setter))
(def upsert (wrap-modify mc/upsert first-id-getter update-version-setter))

;;; utility functions
(defn make-collection [args]            ;TODO
  ;; add indices and stuff on vcs collection to speed-up searching
  )

(comment (mc/insert-and-return (:db db) "test" {:hello "true"})
         (mc/update (:db db) "test" {:hello "true"} {$set {:bye "bye"}})
         (mc/find-maps (:db db) "test" {})
         (def db (.start (new-db "mongodb://127.0.0.1:27017/cleeboDev"))))
