(ns #^{:doc "Version control updates to monger documents. Wraps (some) monger API functions.
             Available wrapped functions have an extra argument `version` in third position
             (after `db` and `coll`), which is used to ensure consistency of updates
             (similarly to timestamp vectors)."}
  cleebo.vcs
  (:refer-clojure :exclude [sort find update remove drop])
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

(defmacro assert-ex-info
  "Evaluates expr and throws an exception if it does not evaluate to logical true."
  [x & args]
  (when *assert*
    `(when-not ~x
       (throw (ex-info ~@args)))))

(defn- assert-version [version doc]
  (let [data {:message :missing-version :data doc}]
    (assert-ex-info version "Document is not vcs-controled" data)))

(defn- assert-sync [version db-version]
  (let [data {:message :version-mismatch :data {:db db-version :user version}}]
    (assert-ex-info (= db-version version) "Version mismatch" data)))

;;; Exceptions
(defn- ex-internal [e]
  (let [{message :message ex :class} (bean e)
        stacktrace (mapv str (.getStackTrace e))]
    (ex-info "Internal Exception"
             {:message :internal :data {:exception (str ex) :stacktrace stacktrace}})))

(defn- ex-insert [doc e]
  (let [{message :message ex :class} (bean e)
        stacktrace (mapv str (.getStackTrace e))]
    (ex-info "Couldn't insert vcs version"
             {:message :insert-error :data {:doc doc :exception (str ex) :stacktrace stacktrace}})))

(defn- ex-version
  "exception for not-matching version updates"
  [{last-vcs-version :_version} {new-doc-version :_version}]
  (ex-info
   "Claimed version not in sync with collection version"
   {:message :unsync-version :data {:last-vcs-version last-vcs-version :claimed-version new-doc-version}}))

(defn- ex-id
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
;;; insert; insert-*; remove; update; update-*;
(defn- with-doc-id
  "rearranges last doc version before inserting it into the vcs coll"
  [{id :_id :as doc}]
  (-> doc (assoc :docId id) (dissoc :_id)))

(defn- insert-version
  "inserts the last modified version of a document into the vcs collection"
  [db {:keys [_version _id] :as doc} & {:keys [merge-field] :as args :or {merge-field :history}}]
  (mc/ensure-index db *hist-coll-name* (array-map :_version 1 :docId 1) {:unique true})
  (timbre/info "Inserting document version" _version "of doc id" _id)
  (try (mc/insert db *hist-coll-name* (with-doc-id doc))
       (catch Throwable t
         (throw (ex-insert doc t)))))

(defn- doc-version-setter [doc]
  (assoc doc :_version 0))

(defn- rollback-update [db coll id doc]
  (timbre/info "Rollbacking update of doc" id "to" doc)
  (mc/update-by-id db coll id doc))

(defn- update-version-document
  "version setter for document updates with mongodb operators; fns: update, update-by-id, find-and-modify"
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
  (try (let [res (apply-monger-thunk) ;applies f with right version
             _ (insert-version db doc)] ;insert previous version in vcs coll
         res)                           ;return updated document
       (catch clojure.lang.ExceptionInfo e
         (let [{message :message doc :data} (ex-data e)]
           (if (= message :insert-error)
             (rollback-update db coll _id doc)
             (throw e))))))

(defn- handle-remove [db coll {:keys [_id] :as doc} apply-monger-thunk]
  (try (apply-monger-thunk)
       (insert-version db (assoc doc :_remove true))
       (catch clojure.lang.ExceptionInfo e
         (let [{message :message doc :data} (ex-data e)]
           (if (= message :insert-error)
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
        (assert-ex-info "Version missing from insert" {:message :missing-version :data new-doc})
        (if concern (f db coll new-doc concern) (f db coll new-doc)))))))

(defn- wrap-modify
  "Function wrapper for native monger `find-and-modify`, `update` & `update-by-id`.
   The wrapped version introduces `version` as an extra argument to the original
   monger f signature in third position, which works as a lock"
  [f]
  (fn wrapped-func
    ([db coll version conditions-or-id document] (wrapped-func db coll version conditions-or-id document {}))
    ([db coll version {:keys [_id] :as conditions-or-id} document {:keys [multi upsert remove] :as opts}]
     (timbre/info opts)
     (assert-ex-info (not multi) ":multi is not allowed" {:message :multi-not-allowed :data opts})
     (assert-ex-info (not upsert) ":upsert is not allowed" {:message :upsert-not-allowed :data opts})
     (assert-ex-info (not remove) ":remove is not allowed" {:message :remove-not-allowed :data opts})
     (if-let [{db-version :_version :as doc} (mc/find-one-as-map db coll {:_id (or _id conditions-or-id)})]
       (let [thunk #(f db coll conditions-or-id (update-version-document document) opts)]
         (assert-version db-version doc) ;version is missing
         (assert-sync version db-version) ;wrong version claimed (caller is out of sync)
         (handle-insert db coll doc thunk)) ;do insert/update and eventual rollback in case of error
       (throw (ex-id (or _id conditions-or-id))))))) ;couldn't find original document

(defn- wrap-remove
  "Function wrapper for native monger `remove` & `remove-by-id`.
   The wrapped version introduces `version` as an extra argument to the original
   monger f signature in third position, which works as a lock"
  [f]
  (fn wrapped-func
    ([db coll version] (wrapped-func db coll version nil))
    ([db coll version {:keys [_id] :as conditions-or-id}]
     (assert-ex-info (or _id conditions-or-id) "Bulk remove is not allowed. Missing id" conditions-or-id)
     (if-let [{db-version :_version :as doc} (mc/find-one-as-map db coll {:_id (or _id conditions-or-id)})]
       (let [thunk #(f db coll conditions-or-id)]
         (assert-version db-version doc)
         (assert-sync version db-version)
         (handle-remove db coll doc thunk))
       (throw (ex-id (or _id conditions-or-id)))))))

;;; public wrapper api
(def find-and-modify
  "(find-and-modify db coll version conditions document {:keys [fields sort remove return-new upsert keywordize]})"
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
(defn drop [db coll]
  (let [ids (->> (mc/find-maps db coll {}) (mapv :_id))]
    (mc/drop db coll)
    (mc/update db *hist-coll-name* {:docId {$in ids}} {$push {:_remove true}} {:multi true})))

;;; utility functions
(defn make-collection [args]            ;TODO
  ;; add indices and stuff on vcs collection to speed-up searching
  )

(defmacro with-hist-coll
  "a macro to avoid referring to lib-config dynamic vars directly"
  [hist-coll & body]
  `(binding [*hist-coll-name* ~hist-coll]
     ~@body))

(defn drop-vcs
  "drops vcs source collection. use carefully."
  [db]
  (mc/drop db *hist-coll-name*))

(comment (defonce db (.start (new-db "mongodb://127.0.0.1:27017/cleeboTest")))
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
