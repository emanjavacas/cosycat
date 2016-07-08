(ns #^{:doc "Version control updates to monger documents. Wraps (some) monger API functions."}
  cleebo.vcs 
  (:refer-clojure :exclude [sort find update remove])
  (:require [cleebo.components.db :refer [new-db]]
            [monger.collection :as mc]
            [monger.query :refer :all]
            [monger.operators :refer :all])
  (:import org.bson.types.ObjectId))

(def ^:dynamic *hist-coll-name* "_vcs")

(defmacro assert-ex-info
  "Evaluates expr and throws an exception if it does not evaluate to logical true."
  [x & args]
  (when *assert*
    `(when-not ~x
       (throw (ex-info ~@args)))))

(defmacro with-hist-coll
  "a macro to avoid referring to lib-config dynamic vars directly"
  [hist-coll & body]
  (binding [*hist-coll-name* ~hist-coll]
    ~@body))

(defn new-uuid
  "Computes a random id which can be used as a uuid for a new document"
  []
  (str (java.util.UUID/randomUUID))
  ;; (ObjectId.) ;or just use mongodb's id
  )

;;; find operations:
;;; find-*
(defn merge-history
  "construct the output vcs doc"
  [doc merge-field vcs-history]
  (assoc doc merge-field vcs-history))

(defn find-history
  "retrieves document history from the backup collection"
  [db doc-id & {:keys [reverse] :or {reverse false}}]
  (not-empty
   (with-collection db *hist-coll-name*
     (find {:docId doc-id})
     (sort (array-map :_version (if reverse 1 -1))))))

(defn with-history
  "appends vcs history in a given document by document field `merge-field`"
  [db {:keys [_id] :as doc} & {:keys [reverse merge-field] :or {reverse false merge-field :history}}]
  (if-let [vcs-history (find-history db _id :reverse reverse)]
    (merge-history doc merge-field vcs-history)
    doc))

;;; modify operations:
;;; insert; insert-*; remove; update; update-*; upsert
(defn ex-version
  "exception for not-matching version updates"
  [{last-vcs-version :_version} {new-doc-version :_version}]
  (ex-info
   "Claimed version not in sync with collection version"
   {:last-vcs-version last-vcs-version
    :claimed-version new-doc-version}))

(defn ex-id
  "exception for not-matching document ids"
  [id]
  (ex-info "Couldn't find document by id" {:id id}))

(defn update-version
  "increase version of a document in a vcs-collection"
  [db coll id]
  (mc/update db coll {:docId id} {$inc {:_version 1}}))

(defn with-doc-id
  "rearranges last doc version before inserting it into the vcs coll"
  [{id :_id :as doc}]
  (assert-ex-info id "Attempt to insert vcs version without reference doc id" {:doc doc})
  (-> doc (assoc :docId id) (dissoc :_id)))

(defn insert-version
  "inserts the last modified version of a document into the vcs collection"
  [db {:keys [_version _id] :as doc} & {:keys [merge-field] :as args :or {merge-field :history}}]
  (assert-ex-info _version "Document is not vcs-controled: _version missing" doc)
  (when-let [vcs-history (find-history db _id :reverse false)]
    (if-not (= _version (inc (:_version (first vcs-history))))
      (throw (ex-version (first vcs-history) doc))))
  (mc/ensure-index db *hist-coll-name* (array-map :_version 1 :docId 1) {:unique true})
  (mc/insert db *hist-coll-name* (with-doc-id doc)))

(defn doc-version-setter [doc]
  (assoc doc :_version 0))

(defn wrap-insert
  "function wrapper for native monger fns that perform inserts"
  ([f] (wrap-insert f doc-version-setter))
  ([f version-setter]
   (fn [db coll doc & args]
     (apply f db coll (version-setter doc) args))))

(defn apply-check-fns [f db coll args check-fns]
  (doseq [check-fn check-fns
          :let [result (apply check-fn f db coll args)]
          :when result]
    (throw (ex-info "Bad input argument" {:reason result}))))

(defn wrap-modify
  "function wrapper for native monger fns that perform updates"
  [f id-getter version-setter & check-fns]
  (fn wrapped-func [db coll & args]
    (apply-check-fns f db coll args check-fns)
    (let [id (apply id-getter db coll args)]
      (if-let [doc (mc/find-one-as-map db coll {:_id id})]  ;1 search
        (insert-version db doc)         ;1 insert & 1 (history) search (for safety reasons)
        (throw (ex-id id))))            ;couldn't find doc by id
    (apply version-setter f db coll args)))  ;applies monger f with appropriate version num

;;; id getters
(defn first-id-getter
  "id getter for fn signatures with doc/condition argument in first position (after db, coll).
   target fns: find-and-modify, remove, update, upsert"
  [db coll {:keys [_id] :as doc} & args]
  (assert-ex-info _id "Only queries by id are allowed. Missing _id" doc)
  _id)

(defn by-id-getter
  "doc getter for fn signatures with id argument in first position (after db, coll).
   target fns: remove-by-id, update-by-id"
  [db coll id & args]
  id)

;;; version setters
(defn update-version-setter
  "version setter for document updates with mongodb operators; fns: update, update-by-id, upsert"
  [f db coll conditions-or-id doc & args]
  (assert (not (contains? conditions-or-id :_version)) "Condition by doc version is not allowed")
  (let [new-doc (merge-with merge doc {$inc {:_version 1}})]
    (apply f db coll conditions-or-id new-doc args)))

(defn last-version-setter
  "version setter for ops removing the document from db, which do not need version update.
   target fns: remove, remove-by-id"
  [f db coll & args]
  (apply f db coll args))

;;; checkers
(defn check-upsert [f db coll conditions opts & args]
  (if (= (:upsert opts) true) ":upsert is not allowed"))

(defn check-multi [f db coll conditions opts & args]
  (if (= (:multi opts) true) ":multi is not allowed"))

;;; public wrapper api
(def find-and-modify
  (wrap-modify mc/find-and-modify first-id-getter update-version-setter check-upsert check-multi))
(def remove (wrap-modify mc/remove first-id-getter last-version-setter))
(def remove-by-id (wrap-modify mc/remove-by-id by-id-getter last-version-setter))
(def update (wrap-modify mc/update first-id-getter update-version-setter check-upsert check-multi))
(def update-by-id
  (wrap-modify mc/update-by-id by-id-getter update-version-setter check-upsert check-multi))
(def insert (wrap-insert mc/insert))
(def insert-and-return (wrap-insert mc/insert-and-return))

;;; save, save-and-return: might insert new document or update
;; (def save (wrap-modify mc/save first-id-getter save-version-setter))
;; (def save-and-return (wrap-modify mc/save-and-return first-id-getter save-version-setter))
;;; upsert: might insert or update
;; (def upsert (wrap-modify mc/upsert first-id-getter update-version-setter))

;;; utility functions
(defn make-collection [args]            ;TODO
  ;; add indices and stuff on vcs collection to speed-up searching
  )

(defn drop-vcs
  "drops vcs source collection. use carefully."
  [db]
  (mc/drop db *hist-coll-name*))

(comment (def db (.start (new-db "mongodb://127.0.0.1:27017/cleeboDev")))
         (def doc (insert-and-return (:db db) "test" {:hello "true"}))
         (update (:db db) "test" (dissoc doc :_version) {$set {:stoff 0}})
         (update (:db db) "test" (dissoc doc :_version) {$inc {:stoff 1}})
         (with-history (:db db) (first (mc/find-maps (:db db) "test" {:_id (:_id doc)}))))
