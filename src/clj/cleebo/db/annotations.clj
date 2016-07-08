(ns cleebo.db.annotations
  (:require [monger.collection :as mc]
            [monger.operators :refer :all]
            [taoensso.timbre :as timbre]
            [schema.core :as s]
            [cleebo.utils :refer [get-token-id deep-merge-with]]
            [cleebo.vcs :as vcs :refer [assert-ex-info]]
            [cleebo.schemas.annotation-schemas :refer [annotation-schema cpos-anns-schema]]
            [cleebo.components.db :refer [new-db colls]]
            [cleebo.db.projects :refer [new-project find-project-by-name]]
            [cleebo.roles :refer [check-annotation-role]]
            [schema.coerce :as coerce]))

;;; EXCEPTIONS
(defn ex-user [username project-name action]
  (ex-info "Action not authorized"
           {:message :not-authorized
            :data {:username username :action action :project project-name}}))

(defn ex-overwrite-span [scope]
  (ex-info "Attempt to overwrite span ann with token ann"
           {:message :wrong-update :data {:scope scope}}))

(defn ex-overwrite-token [scope]
  (ex-info "Attempt to overwrite token ann with span ann"
           {:message :wrong-update :data {:scope scope}}))

(defn ex-overlapping-span [source-scope scope]
  (ex-info "Overlapping span"
           {:message :wrong-update
            :data {:source-scope source-scope :scope scope}}))

;;; CHECKERS
(defn check-user-annotations [{db-conn :db :as db} username project-name action]
  (let [{users :users} (find-project-by-name db project-name)
        {role :role} (some #(when (= username (:username %)) %) users)]
    (when-not (check-annotation-role action role)
      (throw (ex-user username project-name action)))))

(defn check-span-overlap
  "checks if two span annotations overlap returning false if there is overlap"
  [{{{new-B :B new-O :O} :scope} :span :as new-ann}
   {{{old-B :B old-O :O} :scope} :span :as old-ann}]
  (cond
    (and (= new-B old-B) (= new-O old-O))   true
    (and (<= new-B old-O) (<= old-B new-O)) false
    :else true))

;;; FETCHERS
(defn fetch-annotation-by-id [{db-conn :db} project id]
  (mc/find-one-as-map db-conn project {:_id id}))

(defmulti fetch-span-annotation-by-key (fn [db project ann-key {type :type}] type))

(defmethod fetch-span-annotation-by-key "token"
  [{db-conn :db} project ann-key {scope :scope}]
  (mc/find-one-as-map
   db-conn project
   {"ann.key" ann-key $and [{"span.scope.B" {$lte scope}} {"span.scope.O" {$gte scope}}]}))

(defmethod fetch-span-annotation-by-key "IOB"
  [{db-conn :db} project ann-key {{B :B O :O} :scope}]
  (mc/find-one-as-map
   db-conn project
   {"ann.key" ann-key $and [{"span.scope.B" {$lte O}} {"span.scope.O" {$gte B}}]}))

(defmulti fetch-token-annotation-by-key (fn [db project ann-key {type :type}] type))

(defmethod fetch-token-annotation-by-key "token"
  [{db-conn :db} project ann-key {scope :scope}]
  (mc/find-one-as-map
   db-conn project
   {"ann.key" ann-key "span.scope" scope}))

(defmethod fetch-token-annotation-by-key "IOB"
  [{db-conn :db} project key {{B :B O :O} :scope}]
  (mc/find-one-as-map
   db-conn project
   {"ann.key" key "span.scope" {$in (range B (inc O))}}))

(defn fetch-annotations
  [{db-conn :db :as db} project-name from size & {:keys [history] :or {history true} :as opts}]
  (cond->> (mc/find-maps
            db-conn project-name
            {$or [{$and [{"span.scope" {$gte from}} {"span.scope" {$lt (+ from size)}}]}
                  {$or  [{"span.scope.O" {$gte from}} {"span.scope.B" {$lt (+ from size)}}]}]})
    history (mapv (partial vcs/with-history db))))

;;; SETTERS
(defmulti check-insert (fn [db project {{type :type} :span}] type))

(defmethod check-insert "token"
  [{db-conn :db :as db} project {{scope :scope :as span} :span {key :key} :ann}]
  (when-let [existing-span-annotation (fetch-span-annotation-by-key db project key span)]
    (throw (ex-overwrite-span scope))))

(defmethod check-insert "IOB"
  [{db-conn :db :as db} project {{{B :B O :O} :scope :as span} :span {key :key} :ann}]
  (when-let [existing-token-annotation (fetch-token-annotation-by-key db project key span)]
    (throw (ex-overwrite-token span)))
  (when-let [existing-span-annotation (fetch-span-annotation-by-key db project key span)]
    (throw (ex-overwrite-span span))))

(defn insert-annotation [{db-conn :db :as db} project {username :username :as ann}]
  (check-user-annotations db username project :write)
  (check-insert db project ann)
  (vcs/insert-and-return db-conn project (assoc ann :_id vcs/new-uuid)))

(defn update-annotation
  [{db-conn :db :as db} project
   {timestamp :timestamp
    username :username
    {ann-key :key ann-value :value} :ann
    query :query corpus :corpus
    version :_version id :_id  :as ann}
   & {:keys [history] :or {history true}}]
  (check-user-annotations db username project :update)
  (assert-ex-info (and version id) "annotation update requires annotation id" {:reason :missing-id})
  (cond->> (vcs/find-and-modify
            db-conn project
            {:_id id :_version version}
            {$set {"ann.key" ann-key "ann.value" ann-value
                   "timestamp" timestamp "username" username
                   "query" query "corpus" corpus}}
            {:return-new true})
    history (partial vcs/with-history db)))

;; (defonce db (.start (new-db (:database-url config.core/env))))
;; (fetch-anns-in-range db ["user-playground"] 417 418) 417
;; (first (find-ann-ids-in-range db ["user-playground"] 410 420))
