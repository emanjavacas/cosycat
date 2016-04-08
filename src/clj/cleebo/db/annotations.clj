(ns cleebo.db.annotations
  (:require [monger.collection :as mc]
            [monger.operators :refer :all]
            [taoensso.timbre :as timbre]
            [schema.core :as s]
            [cleebo.utils :refer [get-token-id ->int]]
            [cleebo.components.db :refer [new-db]]
            [cleebo.shared-schemas :refer [annotation-schema ann-from-db-schema]]
            [schema.coerce :as coerce]))

;; (def coll "annotations")

;; (defn find-ann-by-key [anns k]
;;   (first (filter #(= k (get-in % [:ann :key])) anns)))

;; (defn update-ann-history [ann]
;;   (concat [(dissoc ann :history)] (:history ann)))

;; (defn- create-annotation
;;   "inserts annotation for a token position without any previous annotations"
;;   [db coll token-id {k :key :as ann}]
;;   (mc/find-and-modify
;;    db coll
;;    {:_id token-id}
;;    {$push {:anns {(keyword k) ann}}}
;;    {:return-new true
;;     :upsert true}))

;; (defn- update-annotation
;;   "inserts annotation for a token position with previous
;;   annotations (updating its history)"
;;   [db coll token-id
;;    {timestamp :timestamp
;;     username :username
;;     {k :key v :value} :ann :as ann}
;;    previous-ann]
;;   (mc/find-and-modify
;;    db coll
;;    {:_id token-id "anns.ann.key" k}
;;    {$set {"anns.$.ann.key" k "anns.$.ann.value" v
;;           "anns.$.username" username "anns.$.timestamp" timestamp
;;           "anns.$.history" (update-ann-history previous-ann)}}
;;    {:return-new true
;;     :upsert true}))

;; (defn- handle-insert
;;   [cpos-anns db token-id {{k :key} :ann :as ann}]
;;   (if (empty? cpos-anns) ;no anns found for token position
;;     (create-annotation db coll token-id ann)
;;     (let [previous-ann (find-ann-by-key (:anns cpos-anns) k)]
;;       (update-annotation db coll token-id ann previous-ann))))

;; ;;; [token-id ann hit-id]
;; ;;; [int      map int   ] normal situation; single ann
;; ;;; [vec      map vec   ] same ann for multiple tokens (doesn't imply mult hit-ids: spans)
;; ;;; [vec      vec vec   ] mult anns for mult tokens (implies mult hit-ids)

;; (defmulti new-token-annotation
;;   "dispatch based on type (either a particular value or 
;;   a vector of that value for bulk annotations)"
;;   (fn [db token-id ann] [(type token-id) (type ann)]))

;; (s/defmethod ^:always-validate new-token-annotation
;;   [java.lang.Long clojure.lang.PersistentArrayMap]
;;   [db token-id :- s/Int {{k :key} :ann as ann} :- annotation-schema]
;;   (let [{db :db} db
;;         [old-anns] (mc/find-maps db coll {:_id token-id "anns.ann.key" k})]
;;     (try (let [{:keys [anns _id]} (handle-insert old-anns db coll token-id ann k)]
;;         {:data {:token-id token-id :anns anns}
;;          :status :ok
;;          :type :annotation})
;;       (catch Exception e
;;         {:data {:token-id token-id
;;                 :reason :internal-error
;;                 :e (str e)}
;;          :status :error
;;          :type :annotation}))))

;; (s/defmethod ^:always-validate new-token-annotation
;;   [clojure.lang.PersistentVector clojure.lang.PersistentArrayMap]
;;   [db token-ids :- [s/Int] the-ann :- annotation-schema]
;;   (vec (for [[token-id ann] (map vector token-ids (repeat the-ann))]
;;       (new-token-annotation db token-id ann))))

;; (s/defmethod ^:always-validate new-token-annotation
;;   [clojure.lang.PersistentVector clojure.lang.PersistentVector]
;;   [db token-ids :- [s/Int] anns :- [annotation-schema]]
;;   (vec (for [[token-id ann] (map vector token-ids anns)]
;;          (new-token-annotation db token-id ann))))

;; (s/defn ^:always-validate fetch-annotations :- ann-from-db-schema
;;   ([db token-id :- s/Int] (fetch-annotations db token-id (inc token-id)))
;;   ([db id-from :- s/Int id-to :- s/Int]
;;    (let [{db :db} db
;;          out (mc/find-maps db coll {$and [{:_id {$gte id-from}} {:_id {$lt id-to}}]})]
;;      (zipmap (map :_id out) out))))

;; (defn merge-annotations-hit
;;   "merge annotations retrieved for the current hit vector with hit vector"
;;   [hit ann-from-db]
;;   (map (fn [token]
;;          (let [id (get-token-id token)]
;;            (if-let [{:keys [anns]} (get ann-from-db id)]
;;              (assoc token :anns anns)
;;              token)))
;;        hit))

;; (defn find-first-id
;;   "finds first non-dummy token (token with non negative id)"
;;   [hit]
;;   (first (drop-while #(neg? %) (map get-token-id hit))))

;; (defn merge-annotations
;;   "collect stored annotations for a given span of hits. Annotations are 
;;   collected at one for a given hit, since we know the token-id range of its
;;   tokens `from`: `to`"
;;   [db results]
;;   (for [{:keys [hit] :as hit-map} results
;;         :let [from (find-first-id hit) ;todo, find first real id (avoid dummies)
;;               to   (find-first-id (reverse hit))
;;               anns-from-db (fetch-annotations db from to)
;;               new-hit (merge-annotations-hit hit anns-from-db)]]
;;     (assoc hit-map :hit new-hit)))

;(def db (.start (new-db {:url "mongodb://127.0.0.1:27017/cleeboTest"})))

;; (mc/find-and-modify
;;  (:db db) coll
;;  {:_id 52}
;;  {$set {:more-anns {(keyword "A") "b"}}}
;;  {:return-new true})
;; (mc/find-and-modify (:db db) coll {:_id 52} {} {:remove true})

;; (find-ann-by-id db coll 417 "noun")

(defn find-ann-id
  "fetchs id of ann in coll `anns` for given key and token-id"
  [{db :db} token-id k]
  (-> (mc/find-one-as-map
       db "cpos-ann"
       {:_id token-id "anns.key" k}
       {"anns.$.key" true "_id" false})
      :anns
      first))

(defn find-ann-ids-in-range [{db :db} id-from id-to]
  (mc/find-maps
   db "cpos-ann"
   {$and [{:_id {$gte id-from}} {:_id {$lt id-to}}]}))

(defn find-ann-by-id
  [{db :db} ann-id]
  (mc/find-one-as-map db "anns" {:_id ann-id} {:_id false}))

(defn insert-annotation
  "creates new ann in coll `anns` for given key and token-id"
  [{db :db} token-id {k :key :as ann}]
  (let [{:keys [_id] :as ann} (mc/insert-and-return db "anns" ann)]
    (mc/find-and-modify
     db "cpos-anns"
     {:_id token-id}
     {$push {:anns {:key k :ann-id _id}}}
     {:upsert-new true})
    (dissoc ann :_id)))

(defn compute-history [ann]
  (let [record-ann (select-keys ann [:ann :username :timestamp])]
    (conj (:history ann) record-ann)))

(defn update-annotation
  "updates existing ann in coll `anns` for given key and token-id"
  [{db-conn :db :as db} token-id
   {timestamp :timestamp
    username :username
    {k :key v :value} :ann :as ann}
   ann-id]
  (mc/find-and-modify
   db-conn "anns"
   {:_id ann-id}
   {$set {"ann.value" v "ann.key" k
          "username" username "timestamp" timestamp
          "history" (compute-history (find-ann-by-id db ann-id))}}
   {:return-new true}))

(defmulti new-token-annotation
  "dispatch based on type (either a particular value or 
  a vector of that value for bulk annotations)"
  (fn [db token-id ann] [(type token-id) (type ann)]))

(s/defmethod ^:always-validate new-token-annotation
  [java.lang.Long clojure.lang.PersistentVector]
  [db token-id :- s/Int {{k :key} :ann :as ann} :- annotation-schema]
  (try
    (let [ann (assoc ann :span {:type "simple" :scope token-id})
          new-ann (if-let [{:keys [ann-id]} (find-ann-id db "cpos-ann" token-id k)]
                    (update-annotation db token-id ann ann-id)
                    (insert-annotation db token-id ann))]
      {:data {:token-id token-id :ann new-ann}
       :status :ok
       :type :annotation})
    (catch Exception e
      {:data {:token-id token-id
              :reason :internal-error
              :e (str e)}
       :status :error
       :type :annotation})))

(s/defmethod ^:always-validate new-token-annotation
  [clojure.lang.PersistentVector clojure.lang.PersistentArrayMap]
  [db token-ids :- [s/Int] the-ann :- annotation-schema]
  (vec (for [[token-id ann] (map vector token-ids (repeat the-ann))]
      (new-token-annotation db token-id ann))))

(s/defmethod ^:always-validate new-token-annotation
  [clojure.lang.PersistentVector clojure.lang.PersistentVector]
  [db token-ids :- [s/Int] anns :- [annotation-schema]]
  (vec (for [[token-id ann] (map vector token-ids anns)]
         (new-token-annotation db token-id ann))))

(defn fetch-anns
  "{token-id {ann-key1 ann ann-key2 ann} token-id2 ...}"
  [{db :db} ann-ids]
  (apply merge-with conj (for [{token-id :_id anns :anns} ann-ids
                               {k :key ann-id :ann-id} anns
                               :let [ann (find-ann-by-id db ann-id)]]
                           {token-id {k ann}})))

(s/defn ^:always-validate fetch-anns-in-range
  ([db token-id :- s/Int] (fetch-annotations db token-id (inc token-id)))
  ([{db-conn :db :as db} id-from :- s/Int id-to :- s/Int]
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
