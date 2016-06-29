(ns cleebo.vcs
  (:refer-clojure :exclude [sort find])
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

(defn merge-history [doc versions merge-field]
  (assoc doc merge-field version))

;;; find operations:
;;; find-*
(defn history
  "seq of version history maps sorted in version order"
  [{db-conn :db :as db} {:keys [_id] :as doc}
   & {:keys [reverse merge-docs] :or {reverse false merge-field :history}}]
  (-> (with-collection db-conn *hist-coll-name*
         (find {:doc-id _id})
         (sort (array-map :_version (if reverse 1 -1))))
      (merge-history doc merge-docs)))

(defn maybe-update-hist [last-doc-version new-doc-version]
  (throw (ex-info "Claimed version not in sync with version collection" new-doc-version)))

(defn insert-new-version
  [{db-conn :db :as db} {:keys [_id version] :as new-doc-version}
   & {:keys [merge-field] :as args :or {merge-field :history}}]
  {:pre [(and _id version)]}
  (let [{vcs-docs merge-field} (history db new-doc-version :reverse false)]
    (if-not (= version (inc (:version (first vcs-docs))))
      (maybe-update-hist (first vcs-docs) new-doc-version)
      (mc/insert db-conn *hist-coll-name* new-doc-version))))

;;; modify operations:
;;; insert; insert-*; remove; rename; save; save-and-return; update; update-*; upsert
(defn wrap-modify [get-object-fn]
  (fn wrapped-func [db coll & args]
    {:pre []}
    (let [current (apply get-object-fn db-conn coll args)]
      (insert-new-version {:db db-conn} ))))

(def insert-and-return (wrap-modify ))
(def find-and-modify (wrap-modify ))
(def remove (wrap-modify ))

;; (mc/insert-and-return (:db db) "test" {:hello "true"})
;; (mc/update (:db db) "test" {:hello "true"} {$set {:bye "bye"}})
;; (mc/find-maps (:db db) "test" {})


;; (def db (.start (new-db "mongodb://127.0.0.1:27017/cleeboDev")))

;; (with-collection (:db db) "users"
;;   (find {}))
