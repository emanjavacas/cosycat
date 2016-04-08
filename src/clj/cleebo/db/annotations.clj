(ns cleebo.db.annotations
  (:require [monger.collection :as mc]
            [monger.operators :refer :all]
            [taoensso.timbre :as timbre]
            [schema.core :as s]
            [cleebo.utils :refer [get-token-id ->int]]
            [cleebo.components.db :refer [new-db]]
            [cleebo.shared-schemas :refer [annotation-schema cpos-ann-schema]]
            [schema.coerce :as coerce]))

;; ;;; [ann hit-id]
;; ;;; [map int   ] normal situation; single ann
;; ;;; [vec vec   ] same ann for multiple tokens (implies mult hit-ids)
;; ;;; [vec vec   ] mult anns for mult tokens (implies mult hit-ids)

(s/defn find-ann-id :- (s/maybe {:key s/Str :ann-id s/Int})
  "fetchs id of ann in coll `anns` for given key and token-id"
  [{db :db} token-id k]
  (-> (mc/find-one-as-map
       db "cpos_ann"
       {:_id token-id "anns.key" k}
       {"anns.$.key" true "_id" false})
      :anns
      first))

(s/defn find-ann-ids-in-range [{db :db} id-from id-to] :- [cpos-ann-schema]
  (mc/find-maps
   db "cpos_ann"
   {$and [{:_id {$gte id-from}} {:_id {$lt id-to}}]}))

(s/defn find-ann-by-id :- annotation-schema
  [{db :db} ann-id]
  (mc/find-one-as-map db "anns" {:_id ann-id} {:_id false}))

(s/defn insert-annotation :- annotation-schema
  "creates new ann in coll `anns` for given ann"
  [{db :db} {{scope :scope} :span {k :key} :ann :as ann}]
  (let [{:keys [_id] :as ann} (mc/insert-and-return db "anns" ann)]
    (timbre/debug ann)
    (mc/find-and-modify
     db "cpos_ann"
     {:_id scope}
     {$push {:anns {:key k :ann-id _id}}}
     {:upsert true})
    (dissoc ann :_id)))

(defn compute-history [ann]
  (let [record-ann (select-keys ann [:ann :username :timestamp])]
    (conj (:history ann) record-ann)))

(s/defn update-annotation :- annotation-schema
  "updates existing ann in coll `anns` for given key"
  [{db-conn :db :as db}
   {timestamp :timestamp
    username :username
    {scope :scope} :span
    {k :key v :value} :ann :as ann}
   ann-id]
  (mc/find-and-modify
   db-conn "anns"
   {:_id ann-id}
   {$set {"ann.value" v "ann.key" k
          "span.type" "token" "span.scope" scope
          "username" username "timestamp" timestamp
          "history" (compute-history (find-ann-by-id db ann-id))}}
   {:return-new true}))

(defmulti new-token-annotation
  "dispatch based on type (either a particular value or 
  a vector of that value for bulk annotations)"
  (fn [db ann] (type ann)))

(s/defmethod ^:always-validate new-token-annotation
  clojure.lang.PersistentArrayMap
  [db {{scope :scope} :span {k :key} :ann :as ann}]
  (try
    (let [new-ann (if-let [{:keys [ann-id]} (find-ann-id db scope k)]
                    (update-annotation db ann ann-id)
                    (insert-annotation db ann))]
      {:data {:ann new-ann}
       :status :ok
       :type :annotation})
    (catch Exception e
      {:data {:scope scope
              :reason :internal-error
              :e (str e)}
       :status :error
       :type :annotation})))

(s/defmethod ^:always-validate new-token-annotation
  clojure.lang.PersistentVector
  [db anns :- [annotation-schema]]
  (mapv (fn [ann] (new-token-annotation db ann)) anns))

(defn fetch-anns
  "{token-id {ann-key1 ann ann-key2 ann} token-id2 ...}"
  [db ann-ids]
  (apply merge-with conj
         (for [{token-id :_id anns :anns} ann-ids
               {k :key ann-id :ann-id} anns
               :let [ann (find-ann-by-id db ann-id)]]
           {token-id {k ann}})))

(s/defn ^:always-validate fetch-anns-in-range
  ([db token-id :- s/Int] (fetch-anns-in-range db token-id (inc token-id)))
  ([db id-from :- s/Int id-to :- s/Int]
   (let [ann-ids (find-ann-ids-in-range db id-from id-to)
         anns-in-range (fetch-anns db ann-ids)]
     anns-in-range)))

(defn merge-annotations-hit
  [hit anns-in-range]
  (map (fn [token]
         (let [id (get-token-id token)]
           (if-let [anns (get anns-in-range id)]
             (assoc token :anns anns)
             token)))
       hit))

(defn find-first-id
  "finds first non-dummy token (token with non negative id)"
  [hit]
  (first (drop-while #(neg? %) (map get-token-id hit))))

(defn merge-annotations
  "collect stored annotations for a given span of hits. Annotations are 
  collected at one for a given hit, since we know the token-id range of its
  tokens `from`: `to`"
  [db results]
  (for [{:keys [hit] :as hit-map} results
        :let [from (find-first-id hit) ;todo, find first real id (avoid dummies)
              to   (find-first-id (reverse hit))
              anns-in-range (fetch-anns-in-range db from to)
              new-hit (merge-annotations-hit hit anns-in-range)]]
    (assoc hit-map :hit new-hit)))

;(def db (.start (new-db {:url "mongodb://127.0.0.1:27017/cleeboTest"})))

;; (mc/find-and-modify
;;  (:db db) coll
;;  {:_id 52}
;;  {$set {:more-anns {(keyword "A") "b"}}}
;;  {:return-new true})
;; (mc/find-and-modify (:db db) coll {:_id 52} {} {:remove true})

;; (find-ann-by-id db coll 417 "noun")
