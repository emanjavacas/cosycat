(ns #^{:doc "Version control updates to monger documents. Wraps (some) monger API functions.
             Available wrapped functions have an extra argument `version` in third position
             (after db and coll), which is used to ensure consistency of updates (similarly
             to timestamp vectors)."}
  cleebo.vcs
  (:refer-clojure :exclude [sort find update remove])
  (:require [cleebo.components.db :refer [new-db]]
            [monger.core :as mg]
            [monger.collection :as mc]
            [monger.query :refer :all]
            [monger.operators :refer :all]
            [taoensso.timbre :as timbre])
  (:import org.bson.types.ObjectId))

;;; Config vars
(def ^:dynamic *hist-coll-name* "_vcs")

;;; Util funcs
(defn deep-merge
   "Recursively merges maps. If keys are not maps, the last value wins."
   [& vals]
   (if (every? map? vals)
     (apply merge-with deep-merge vals)
     (last vals)))

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

;;; Exceptions
(defn ex-internal [e]
  (let [{:keys [message :message ex :class]} (bean e)
        stacktrace (mapv str (.getStackTrace e))]
    (ex-info "Internal Exception"
             {:message :internal :data {:exception (str ex) :stacktrace stacktrace}})))

(defn ex-insert [doc]
  (ex-info "Couldn't insert vcs version" {:message :insert-error :data doc}))

(defn ex-version
  "exception for not-matching version updates"
  [{last-vcs-version :_version} {new-doc-version :_version}]
  (ex-info
   "Claimed version not in sync with collection version"
   {:message :unsync-version :data {:last-vcs-version last-vcs-version :claimed-version new-doc-version}}))

(defn ex-id
  "exception for not-matching document ids"
  [id]
  (ex-info "Couldn't find document by id" {:message :doc-not-found :data {:id id}}))

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
  [db {:keys [_id] :as doc}
   & {:keys [reverse merge-field on-history-doc] :or {reverse false merge-field :history}}]
  (if-let [vcs-history (find-history db _id :reverse reverse)]
    (merge-history doc merge-field (cond->> vcs-history on-history-doc (mapv on-history-doc)))
    doc))

;;; modify operations:
;;; insert; insert-*; remove; update; update-*; upsert
(defn with-doc-id
  "rearranges last doc version before inserting it into the vcs coll"
  [{id :_id :as doc}]
  (-> doc (assoc :docId id) (dissoc :_id)))

(defn insert-version
  "inserts the last modified version of a document into the vcs collection"
  [db {:keys [_version _id] :as doc} & {:keys [merge-field] :as args :or {merge-field :history}}]
  (mc/ensure-index db *hist-coll-name* (array-map :_version 1 :docId 1) {:unique true})
  (timbre/info "Inserting document version" _version "of doc id" _id)
  (try (mc/insert db *hist-coll-name* (with-doc-id doc))
       (catch Throwable t
         (throw (ex-insert doc)))))

(defn doc-version-setter [doc]
  (assoc doc :_version 0))

(defn wrap-insert
  "function wrapper for native monger fns that perform inserts"
  ([f] (wrap-insert f doc-version-setter))
  ([f version-setter]
   (fn [db coll doc & args]
     (let [{version :_version :as new-doc} (version-setter doc)]
       (assert-ex-info "Version missing from insert" {:message :missing-version :data new-doc})
       (apply f db coll new-doc args)))))

(defn apply-check-fns [f db coll args check-fns]
  (doseq [check-fn check-fns
          :let [result (apply check-fn f db coll args)]
          :when result]
    (throw (ex-info "Bad input argument" {:message result}))))

(defn unroll-update [db coll id doc]
  (timbre/info "Unrolling update of doc" id "to" doc)
  (mc/update-by-id db coll id doc))

(defn wrap-modify
  "Function wrapper for native monger fns that perform updates.
   The wrapped version introduces `version` as an extra argument to the original
   monger f signature in third position, which works as a lock.
   `id-getter` is a function that retrieves the document id from the user monger call,
   `version-setter` is a function that run a modified version of the user moger call,
   which takes care of updating the resulting document version"
  [f {:keys [id-getter version-setter]} & check-fns]
  (fn wrapped-func [db coll version & args]
    (apply-check-fns f db coll args check-fns)
    (let [id (apply id-getter db coll args)]
      (if-let [{db-version :_version :as doc} (mc/find-one-as-map db coll {:_id id})] ;1 search
        (do (assert-ex-info
             db-version "Document is not vcs-controled"
             {:message :missing-version :data doc})
            (assert-ex-info
             (= db-version version) "Version mismatch"
             {:message :version-mismatch :data {:db db-version :user version}})
            (try (let [res (apply version-setter f db coll args)] ;applies monger-f with right version
                   (insert-version db doc)
                   res)                 ;return updated document
                 (catch clojure.lang.ExceptionInfo e
                   (let [{message :message doc :data} (ex-data e)]
                     (if (= message :insert-error)
                       (unroll-update db coll id doc)
                       (throw e)))))) ;1 insert & 1 (history) search (for safety reasons)
        (throw (ex-id id))))))  ;couldn't find doc by id

;;; id getters
(defn first-id-getter
  "id getter for fn signatures with doc/condition argument in first position (after db, coll).
   target fns: find-and-modify, remove, update, upsert"
  [db coll {:keys [_id] :as doc} & args]
  (assert-ex-info _id "Query by id needed. Missing id" {:message :missing-id :data doc})
  _id)

(defn by-id-getter
  "doc getter for fn signatures with id argument in first position (after db, coll).
   target fns: remove-by-id, update-by-id"
  [db coll id & args]
  id)

;;; version setters
(defn update-version-setter
  "version setter for document updates with mongodb operators; fns: update, update-by-id, find-and-modify"
  [f db coll conditions-or-id doc & opts]
  (let [new-doc (deep-merge doc {$inc {:_version 1}})]
    (timbre/info "Applying update-map" new-doc)
    (apply f db coll conditions-or-id new-doc opts)))

(defn last-version-setter
  "version setter for ops removing the document from db, which do not need version update.
   target fns: remove, remove-by-id"
  [f db coll conditions-or-id & _]
  (f db coll conditions-or-id))

;;; checkers
(defn check-upsert [f db coll conditions doc & [opts]]
  (if (= (:upsert opts) true) :upsert-not-allowed))

(defn check-multi [f db coll conditions doc & [opts]]
  (if (= (:multi opts) true) :multi-not-allowed))

;;; public wrapper api
(def find-and-modify
  (wrap-modify
   mc/find-and-modify
   {:id-getter first-id-getter :version-setter update-version-setter}
   check-upsert check-multi))

(def remove
  (wrap-modify
   mc/remove
   {:id-getter first-id-getter
    :version-setter last-version-setter}))

(def remove-by-id
  (wrap-modify
   mc/remove-by-id
   {:id-getter by-id-getter
    :version-setter last-version-setter}))

(def update
  (wrap-modify
   mc/update
   {:id-getter first-id-getter
    :version-setter update-version-setter}
   check-upsert
   check-multi))

(def update-by-id
  (wrap-modify
   mc/update-by-id
   {:id-getter by-id-getter
    :version-setter update-version-setter}
   check-upsert
   check-multi))

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

(comment (defonce db (.start (new-db "mongodb://127.0.0.1:27017/cleeboTest")))
         (def doc (insert-and-return (:db db) "test" {:hello "true"}))
         (update (:db db) "test" 0 (dissoc doc :_version) {$set {:stoff 0}})
         (update (:db db) "test" 1 (dissoc doc :_version) {$inc {:stoff 1}})
         (with-history (:db db) (first (mc/find-maps (:db db) "test" {:_id (:_id doc)}))))
