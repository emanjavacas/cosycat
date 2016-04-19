(ns cleebo.db.annotations
  (:require [monger.collection :as mc]
            [monger.operators :refer :all]
            [taoensso.timbre :as timbre]
            [schema.core :as s]
            [cleebo.utils :refer [get-token-id ->int]]
            [cleebo.components.db :refer [new-db]]
            [cleebo.schemas.annotation-schemas :refer [annotation-schema cpos-ann-schema]]
            [schema.coerce :as coerce]))

(defn check-span-overlap
  "checks if two span annotations overlap returning false if there is overlap"
  [{{{new-B :B new-O :O} :scope} :span :as new-ann}
   {{{old-B :B old-O :O} :scope} :span :as old-ann}]
  (cond
    (and (= new-B old-B) (= new-O old-O))   true
    (and (<= new-B old-O) (<= old-B new-O)) false
    :else true))

(defmulti find-ann-id
  "fetchs id of ann in coll `anns` for given a new annotation,
  span annotations are fetched according to B-cpos"
  (fn [db {{type :type} :span}] type))

(defmethod find-ann-id
  "token"
  [{db :db} {{scope :scope} :span {k :key} :ann}]
  (if-let [{{old-scope :scope} :span}
           (mc/find-one-as-map
            db "anns"
            {"ann.key" k
             "span.type" "IOB"          ;superflous
             $and [{"span.scope.B" {$lte scope}}
                   {"span.scope.O" {$gte scope}}]})]
    (throw (ex-info "Attempt to overwrite span annotation with token annotation"
                    {:scope old-scope}))
    (-> (mc/find-one-as-map
         db "cpos_ann"
         {:_id scope "anns.key" k}
         {"anns.$.key" true "_id" false})
        :anns
        first)))

(defmethod find-ann-id
  "IOB"
  [{db :db} {{{new-B :B new-O :O :as new-scope} :scope} :span {k :key} :ann :as ann-map}]
  (when-let [{{scope :scope} :span}
             (mc/find-one-as-map
              db "anns"
              {"ann.key" k
               "span.scope" {$in (range new-B (inc new-O))}})]
    (throw (ex-info "Attempt to overwrite token annotation with span annotation" {:scope scope})))
  (when-let [{{{old-B :B old-O :O :as old-scope} :scope} :span ann-id :_id}
             (mc/find-one-as-map
              db "anns"
              {"ann.key" k
               $and [{"span.scope.B" {$lte new-O}}
                     {"span.scope.O" {$gte new-B}}]})]
    (if-not (and (= old-B new-B) (= old-O new-O))
      (throw (ex-info "Overlapping span" {:old-scope old-scope :new-scope new-scope}))
      {:ann-id ann-id})))

(s/defn find-ann-ids-in-range [{db :db} id-from id-to] :- [cpos-ann-schema]
  (mc/find-maps
   db "cpos_ann"
   {$and [{:_id {$gte id-from}} {:_id {$lt id-to}}]}))

(s/defn find-ann-by-id :- annotation-schema
  [{db :db} ann-id]
  (mc/find-one-as-map db "anns" {:_id ann-id} {:_id false}))

(defn compute-history [ann]
  (let [record-ann (select-keys ann [:ann :username :timestamp])]
    (conj (:history ann) record-ann)))

(defmulti insert-annotation
  "creates new ann in coll `anns` for given ann"
  (fn [db {{t :type} :span}] t))

(s/defmethod insert-annotation
  "token"
  :- annotation-schema
  [{db :db} {{scope :scope} :span {k :key} :ann :as ann}]
  (let [{:keys [_id] :as ann} (mc/insert-and-return db "anns" ann)]
    (mc/find-and-modify
     db "cpos_ann"
     {:_id scope}
     {$push {:anns {:key k :ann-id _id}}}
     {:upsert true})
    ann))

(s/defmethod insert-annotation
  "IOB"
  :- annotation-schema
  [{db :db} {{{B :B O :O} :scope} :span {k :key} :ann :as ann}]
  (let [{:keys [_id] :as ann} (mc/insert-and-return db "anns" ann)]
    (doseq [token-id (range B (inc O))]
      (mc/find-and-modify
       db "cpos_ann"
       {:_id token-id}
       {$push {:anns {:key k :ann-id _id}}}
       {:upsert true}))
    ann))

(defmulti update-annotation
  "updates existing ann in coll `anns` for given key"
  (fn [db {{t :type} :span} ann-id] t))

(s/defmethod update-annotation
  "token"
  :- annotation-schema
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

(s/defmethod update-annotation
  "IOB"
  :- annotation-schema
  [{db-conn :db :as db}
   {timestamp :timestamp
    username :username
    {{B :B O :O :as scope} :scope :as span} :span
    {k :key v :value} :ann :as ann}
   ann-id]
  (mc/find-and-modify
   db-conn "anns"
   {:_id ann-id}
   {$set {"ann.value" v "ann.key" k
          "span.type" "IOB" "span.scope" scope
          "username" username "timestamp" timestamp
          "history" (compute-history (find-ann-by-id db ann-id))}}
   {:return-new true}))

(defmulti new-token-annotation
  "dispatch based on type (either a particular value or 
  a vector of that value for bulk annotations)"
  (fn [db username ann] (type ann)))

(s/defmethod ^:always-validate new-token-annotation
  clojure.lang.PersistentArrayMap
  [db username {span :span :as ann-map} :- annotation-schema]
  (try
    (let [ann-map (assoc ann-map :username username)
          new-ann (if-let [{:keys [ann-id]} (find-ann-id db ann-map)]
                    (update-annotation db ann-map ann-id)
                    (insert-annotation db ann-map))]
      {:data {:ann-map (dissoc new-ann :_id)}
       :status :ok
       :type :annotation})
    (catch Exception e
      {:data {:span span
              :reason :internal-error
              :e (str e)}
       :status :error
       :type :annotation})))

(s/defmethod ^:always-validate new-token-annotation
  clojure.lang.PersistentVector
  [db username anns :- [annotation-schema]]
  (mapv (fn [ann] (new-token-annotation db username ann)) anns))

(s/defn ^:always-validate fetch-anns :- (s/maybe {s/Int {s/Str annotation-schema}})
  "{token-id {ann-key1 ann ann-key2 ann} token-id2 ...}.
   [enhance] a single span annotation will be fetched as many times as tokens it spans"
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

;; (def db (.start (new-db {:url "mongodb://127.0.0.1:27017/cleeboTest"})))

;; (mc/find-and-modify
;;  (:db db) coll
;;  {:_id 52}
;;  {$set {:more-anns {(keyword "A") "b"}}}
;;  {:return-new true})
;; (mc/find-and-modify (:db db) coll {:_id 52} {} {:remove true})

;; (find-ann-by-id db coll 417 "noun")
