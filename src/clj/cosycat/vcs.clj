(ns #^{:doc "Version control updates to monger documents. Wraps (some) monger API functions.
             Available wrapped functions have an extra argument `version` in third position
             (after `db` and `coll`), which is used to ensure consistency of updates
             (similarly to timestamp vectors)."}
  cosycat.vcs
  (:refer-clojure :exclude [sort find update remove drop])
  (:require [monger.core :as mg]
            [monger.collection :as mc]
            [monger.query :refer :all]
            [monger.operators :refer :all]
            [taoensso.timbre :as timbre])
  (:import org.bson.types.ObjectId))

;;; Config vars
(def ^:dynamic *hist-coll-name* "_vcs")

;;; Util funcs
(defn- deep-merge
   "Recursively merges maps. If keys are not maps, the last value wins."
   [& vals]
   (if (every? map? vals)
     (apply merge-with deep-merge vals)
     (last vals)))

(defn new-uuid
  "Computes a random id which can be used as a uuid for a new document"
  []
  (str (java.util.UUID/randomUUID))
  ;; (ObjectId.) ;or just use mongodb's id
  )

(defn format-stacktrace [stacktrace]
  (apply str (interleave (repeat "\t") stacktrace (repeat "\n"))))

(defmacro assert-ex-info
  "Evaluates expr and throws an exception if it does not evaluate to logical true."
  [x & args]
  (when *assert*
    `(when-not ~x
       (throw (ex-info ~@args)))))

(defn- assert-version [version doc]
  (let [message "Document is not vcs-controled"
        data {:message message :code :missing-version :data doc}]
    (assert-ex-info version message data)))

(defn- assert-sync [version db-version]
  (let [message "Version mismatch"
        data {:message message :code :version-mismatch :data {:db db-version :user version}}]
    (assert-ex-info (= db-version version) message data)))

;;; Exceptions
(defn- ex-insert [doc e]
  (let [{ex :class} (bean e)
        stacktrace (mapv str (.getStackTrace e))
        message "Couldn't insert vcs version"]
    (ex-info message
             {:message message
              :code :insert-error
              :data {:doc doc :exception (str ex) :stacktrace stacktrace}})))

(defn- ex-id
  "exception for not-matching document ids"
  [id]
  (let [message "Couldn't find document by id"]
    (ex-info message {:message message :code :doc-not-found :data {:id id}})))

;;; find operations:
;;; find-*
(defn merge-history
  "construct the output vcs doc"
  [doc merge-field vcs-history]
  (assoc doc merge-field vcs-history))

(defn find-history
  "retrieves document history from the backup collection
   a truthy value to `reverse` returns the history starting
   from the beginning (previous in time)"
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
;;; insert; insert-*; remove; update; update-*;
(defn- with-doc-id
  "rearranges last doc version before inserting it into the vcs coll"
  [{id :_id :as doc}]
  (-> doc (assoc :docId id) (dissoc :_id)))

(defn- insert-version
  "inserts the last modified version of a document into the vcs collection.
   Note that any write attempt on the target collection (not the _vcs collection) that results
   in a duplicate insert into the _vcs collections (as per _version and docId fields), will fail"
  [db {:keys [_version _id] :as doc} & {:keys [merge-field] :as args :or {merge-field :history}}]
  (mc/ensure-index db *hist-coll-name* (array-map :_version 1 :docId 1) {:unique true})
  (timbre/info "Inserting document version" _version "of doc" _id)
  (try (mc/insert db *hist-coll-name* (with-doc-id doc))
       (catch com.mongodb.DuplicateKeyException e
         (timbre/info "Found duplicate version of doc" _id "with version" _version)
         (throw (ex-insert doc e)))
       (catch Throwable e
         (timbre/info "Caught internal exception [" (str e) "]")
         (throw (ex-insert doc e)))))

(defn- doc-version-setter [doc]
  (assoc doc :_version 0))

(defn- rollback-update [db coll id doc]
  (timbre/info "Rollbacking update of doc" id "to" doc)
  (mc/update-by-id db coll id doc)
  ;; return doc to account for :return-new cases
  doc)

(defn- update-version-document
  "version setter for doc updates with mongodb operators; fns: update, update-by-id, find-and-modify"
  [update-document]
  (let [new-doc (deep-merge update-document {$inc {:_version 1}})]
    (timbre/info "Applying update-map" new-doc)
    new-doc))

(defn- handle-insert
  "Auxiliary f that applies wrapped monger function with caller parameters and handles vcs insert.
   `apply-monger-thunk` is a thunk that performs monger f modifications ensuring right doc version
   and inserting the doc version previous to the modification into the vcs collection.
   It returns the updated document in case of successfull update."
  [db coll {:keys [_id] :as doc} apply-monger-thunk]
  (try (let [;; applies f with right version
             res (apply-monger-thunk)
             ;; insert previous version in vcs coll
             _ (insert-version db doc)]
         ;; return updated document
         res)
       (catch clojure.lang.ExceptionInfo e
         (let [{code :code {doc :doc e :exception st :stacktrace} :data} (ex-data e)]
           (timbre/debug "Caught Exception: [" e "]\nStacktrace:\n" (format-stacktrace st))
           (if (= code :insert-error)
             (rollback-update db coll _id doc)
             (throw e))))))

(defn- handle-remove [db coll {:keys [_id] :as doc} apply-monger-thunk]
  (try (apply-monger-thunk)
       (insert-version db (assoc doc :_remove true))
       (catch clojure.lang.ExceptionInfo e
         (let [{code :code {doc :doc e :exception st :stacktrace} :data} (ex-data e)]
           (timbre/debug "Caught Exception: [" e "]\nStacktrace:\n" (format-stacktrace st))
           (if (= code :insert-error)
             (rollback-update db coll _id doc)
             (throw e))))))

(defn- wrap-insert
  "function wrapper for native monger fns that perform inserts"
  ([f] (wrap-insert f doc-version-setter))
  ([f version-setter]
   (fn wrapped-func
     ([db coll doc] (wrapped-func db coll doc nil))
     ([db coll doc concern]
      (let [{version :_version :as new-doc} (version-setter doc)]
        (assert-version version new-doc)
        (if concern
          (f db coll new-doc concern)
          (f db coll new-doc)))))))

(defn- wrap-modify
  "Function wrapper for native monger `find-and-modify`, `update` & `update-by-id`.
   The wrapped version introduces `version` as an extra argument to the original
   monger f signature in third position, which works as a lock"
  [f]
  (fn wrapped-func
    ([db coll version conds-or-id document]
     (wrapped-func db coll version conds-or-id document {}))
    ([db coll version {:keys [_id] :as conds-or-id} document {:keys [multi upsert remove] :as opts}]
     (assert-ex-info (not multi) ":multi is not allowed" {:message :multi-not-allowed :data opts})
     (assert-ex-info (not upsert) ":upsert is not allowed" {:message :upsert-not-allowed :data opts})
     (assert-ex-info (not remove) ":remove is not allowed" {:message :remove-not-allowed :data opts})
     (if-let [{db-version :_version :as doc} (mc/find-one-as-map db coll {:_id (or _id conds-or-id)})]
       (let [thunk #(f db coll conds-or-id (update-version-document document) opts)]
         ;; version is missing
         (assert-version db-version doc)
         ;; wrong version claimed (caller is out of sync)
         (assert-sync version db-version)
         ;; do insert/update and eventual rollback in case of error
         (handle-insert db coll doc thunk))
       ;; couldn't find original document
       (throw (ex-id (or _id conds-or-id)))))))

(defn- wrap-remove
  "Function wrapper for native monger `remove` & `remove-by-id`.
   The wrapped version introduces `version` as an extra argument to the original
   monger f signature in third position, which works as a lock"
  [f]
  (fn wrapped-func
    ([db coll version] (wrapped-func db coll version nil))
    ([db coll version {:keys [_id] :as conds-or-id}]
     (assert-ex-info (or _id conds-or-id) "Bulk remove is not allowed. Missing id" conds-or-id)
     (if-let [{db-version :_version :as doc} (mc/find-one-as-map db coll {:_id (or _id conds-or-id)})]
       (let [thunk #(f db coll conds-or-id)]
         (assert-version db-version doc)
         (assert-sync version db-version)
         (handle-remove db coll doc thunk))
       (throw (ex-id (or _id conds-or-id)))))))

;;; public wrapper api
(def find-and-modify
  "(find-and-modify db coll version cons doc {:keys [fields sort remove return-new keywordize]})"
  (wrap-modify mc/find-and-modify))

(def update
  "(update db coll version conditions document) 
   (update db coll version conditions document {:keys [upsert multi write-concern]})"
  (wrap-modify mc/update))

(def update-by-id
  "(update-by-id db coll version id document)
   (update-by-id db coll version id document {:keys [upsert write-concern]})"
  (wrap-modify mc/update-by-id))

(def remove
  "Vcs version of remove. Attaches a field :_remove with boolean value `true` to the last version.
   (remove db coll version)
   (remove db coll version conditions)"
  (wrap-remove mc/remove))

(def remove-by-id
  "Vcs version of remove. Attaches a field :_remove with boolean value `true` to the last version.
  (remove-by-id db coll version id)"
  (wrap-remove mc/remove-by-id))

;;; save, save-and-return: might insert new document or update
;;; upsert: might insert or update

;;; insert
(def insert
  "(insert db coll document)
   (insert db coll document concern)"
  (wrap-insert mc/insert))

(def insert-and-return
  "(insert-and-return db coll document)
   (insert-and-return db coll document concern)"
  (wrap-insert mc/insert-and-return))

;;; collection-wide operations
(defn bulk-remove
  "remove all vcs-docs from the vcs collection matching `conditions` as per `mongo.collection/remove`"
  [db conditions]
  (mc/remove db *hist-coll-name* conditions))

(defn drop
  "remove a vcs-controlled collection, if `set-remove` is true all vcs-versions of docs in 
   that collection are given a field `:_remove` with value `true`"
  [db coll & {:keys [set-remove] :or {set-remove true}}]
  (let [ids (->> (mc/find-maps db coll {}) (mapv :_id))]
    (mc/drop db coll)
    (if set-remove
      (mc/update db *hist-coll-name* {:docId {$in ids}} {$push {:_remove true}} {:multi true})
      (bulk-remove db {:docId {$in ids}}))))

;;; utility functions
(defn ensure-vcs-indices [db]
  ;; TODO: add indices and stuff on vcs collection to speed-up searching
  (mc/ensure-index db *hist-coll-name* (array-map :_version 1 :docId 1) {:unique true}))

(defmacro with-hist-coll
  "a macro to avoid referring to lib-config dynamic vars directly"
  [hist-coll & body]
  `(binding [*hist-coll-name* ~hist-coll]
     ~@body))

(defn drop-vcs
  "drops vcs source collection. use carefully."
  [db]
  (mc/drop db *hist-coll-name*))

(comment (defonce db (.start (new-db "mongodb://127.0.0.1:27017/cosycatTest")))
         (def data {:id 1234567890 :ann {:key "animacy" :value "true"}
                    :username "user" :timestamp 12324567890
                    :span {:type "IOB" :scope {:B 0 :O 3}}
                    :project "project"})
         (with-hist-coll "my_test_coll"
           (insert-and-return  (:db db) "test" data))
         (def doc (insert-and-return (:db db) "test" data))
         (update (:db db) "test" 0 (dissoc doc :_version) {$set {:stoff 0}} {:upsert true})
         (update (:db db) "test" 1 (dissoc doc :_version) {$inc {:stoff 1}})
         (with-history (:db db) (first (mc/find-maps (:db db) "test" {:_id (:_id doc)})))

         (def coll "test")
         (let [{version :_version id :_id :as doc} (insert-and-return (:db db) coll data)
               _ (update (:db db) coll version {:_id id} {$set {:randomField 0}} {:return-new true})
               _ (update (:db db) coll (inc version) {:_id id} {$inc {:randomField 1}} {:return-new true})
               doc (find-and-modify (:db db) coll (inc (inc version)) {:_id id} {$set {:value "false"}} {:return-new true})]
           doc))
