(ns cosycat.db.annotations
  (:require [monger.collection :as mc]
            [monger.operators :refer :all]
            [taoensso.timbre :as timbre]
            [cosycat.app-utils :refer [server-project-name]]
            [cosycat.utils :refer [assert-ex-info]]
            [cosycat.vcs :as vcs]))

;;; Exceptions
(defn span-or-token [{{B :B O :O :as scope} :scope}]
  (cond (and B O) "span"
        (integer? scope) "token"
        :else "undefined"))

(defn ex-overwrite-span [source-span span]
  (ex-info (str "Attempt to overwrite span annotation with "
                (span-or-token source-span) " annotation")
           {:source-span source-span :span span}))

(defn ex-overwrite-token [source-span span]
  (ex-info (str "Attempt to overwrite token annotation with "
                (span-or-token source-span) " annotation")
           {:source-span source-span :span span}))

(defn ex-overlapping-span [source-span span]
  (ex-info "Attempt to overwrite overlapping span annotation"
           {:source-span source-span :span span}))

;;; Checkers
(declare fetch-span-annotation-by-key fetch-token-annotation-by-key)

(defn check-span-overlap
  "checks if two span annotations overlap returning false if there is overlap"
  [{{{new-B :B new-O :O} :scope} :span :as new-ann}
   {{{old-B :B old-O :O} :scope} :span :as old-ann}]
  (cond
    (and (= new-B old-B) (= new-O old-O))   true
    (and (<= new-B old-O) (<= old-B new-O)) false
    :else true))

(defmulti check-insert (fn [db project {{type :type} :span}] type))

(defmethod check-insert "token"
  [{db-conn :db :as db} project {corpus :corpus {scope :scope :as span} :span {key :key} :ann}]
  (when-let [existing-token-annotation (fetch-token-annotation-by-key db project corpus key span)]
    (throw (ex-overwrite-token (:span existing-token-annotation) span)))
  (when-let [existing-span-annotation (fetch-span-annotation-by-key db project corpus key span)]
    (throw (ex-overwrite-span (:span existing-span-annotation) span))))

(defmethod check-insert "IOB"
  [{db-conn :db :as db} project {corpus :corpus {{B :B O :O} :scope :as span} :span {key :key} :ann}]
  (when-let [existing-token-annotation (fetch-token-annotation-by-key db project corpus key span)]
    (throw (ex-overwrite-token (:span existing-token-annotation) span)))
  (when-let [existing-span-annotation (fetch-span-annotation-by-key db project corpus key span)]
    (throw (ex-overlapping-span (:span existing-span-annotation) span))))

;;; Fetchers
(defn fetch-annotation-by-id [{db-conn :db} project id]
  (mc/find-one-as-map db-conn (server-project-name project) {:_id id}))

(defmulti fetch-span-annotation-by-key (fn [db project corpus ann-key {type :type}] type))

(defmethod fetch-span-annotation-by-key "token"
  [{db-conn :db} project corpus ann-key {scope :scope}]
  (mc/find-one-as-map
   db-conn (server-project-name project)
   {"ann.key" ann-key "corpus" corpus
    $and [{"span.scope.B" {$lte scope}} {"span.scope.O" {$gte scope}}]}))

(defmethod fetch-span-annotation-by-key "IOB"
  [{db-conn :db} project corpus ann-key {{B :B O :O} :scope}]
  (mc/find-one-as-map
   db-conn (server-project-name project)
   {"ann.key" ann-key "corpus" corpus
    $and [{"span.scope.B" {$lte O}} {"span.scope.O" {$gte B}}]}))

(defmulti fetch-token-annotation-by-key (fn [db project corpus ann-key {type :type}] type))

(defmethod fetch-token-annotation-by-key "token"
  [{db-conn :db} project corpus ann-key {scope :scope}]
  (mc/find-one-as-map
   db-conn (server-project-name project)
   {"ann.key" ann-key "corpus" corpus "span.scope" scope}))

(defmethod fetch-token-annotation-by-key "IOB"
  [{db-conn :db} project corpus key {{B :B O :O} :scope}]
  (mc/find-one-as-map
   db-conn (server-project-name project)
   {"ann.key" key "corpus" corpus "span.scope" {$in (range B (inc O))}}))

(defn with-history
  "aux func to avoid sending bson-objectids to the client"
  [db doc]
  (vcs/with-history db doc :on-history-doc #(dissoc % :_id :docId)))

(defn fetch-annotations
  [{db-conn :db :as db} project corpus from size & {:keys [history doc] :or {history true} :as opts}]
  (cond->> (mc/find-maps
            db-conn (server-project-name project)
            {"corpus" corpus
             "span.doc" doc ;if doc is null, get docs without span.doc and docs with span.doc == null
             $or [{$and [{"span.scope" {$gte from}} {"span.scope" {$lt (+ from size)}}]}
                  {$and [{"span.scope.B" {$lte (+ from size)}} {"span.scope.O" {$gt from}}]}]})
    history (mapv (partial with-history db-conn))))

;;; Setters
(defn insert-annotation
  [{db-conn :db :as db} project {username :username :as ann}]
  (check-insert db project ann)
  (vcs/insert-and-return db-conn (server-project-name project) (assoc ann :_id (vcs/new-uuid))))

(defn update-annotation
  [{db-conn :db :as db} project
   {username :username timestamp :timestamp query :query hit-id :hit-id corpus :corpus
    value :value version :_version id :_id :as update-map}
   & {:keys [history] :or {history true}}]
  (assert-ex-info (and version id) "annotation update requires annotation id/version" update-map)
  (cond->> (vcs/find-and-modify
            db-conn (server-project-name project)
            version  
            {:_id id}    ;conditions
            {$set {"ann.value" value "timestamp" timestamp "username" username
                   "query" query "corpus" corpus "hit-id" hit-id}}
            {:return-new true})
    history (with-history db-conn)))

(defn remove-annotation
  [{db-conn :db :as db} project {version :_version id :_id :as ann-map}]
  (assert-ex-info (and version id) "annotation update requires annotation id/version" ann-map)
  (vcs/remove-by-id db-conn (server-project-name project) version id))
