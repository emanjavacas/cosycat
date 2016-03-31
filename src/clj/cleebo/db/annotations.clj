(ns cleebo.db.annotations
  (:require [monger.collection :as mc]
            [monger.operators :refer :all]
            [taoensso.timbre :as timbre]
            [schema.core :as s]
            [cleebo.utils :refer [get-token-id ->int]]
            [cleebo.components.db :refer [new-db]]
            [cleebo.shared-schemas :refer [annotation-schema ann-from-db-schema]]
            [schema.coerce :as coerce]))

(def coll "annotations")
;;; todo, add corpus information

(defn- new-annotation [db coll cpos ann]
  (mc/find-and-modify
   db coll
   {:_id cpos}
   {$push {:anns ann}}
   {:return-new true
    :upsert true}))

(defn update-annotation
  [db coll cpos
   {timestamp :timestamp
    username :username
    {k :key v :value} :ann :as ann}
   old-ann]
  (mc/find-and-modify
   db coll
   {:_id cpos "anns.ann.key" k}
   {$set {"anns.$.ann.key" k "anns.$.ann.value" v
          "anns.$.username" username "anns.$.timestamp" timestamp
          "anns.$.history" (concat [(dissoc old-ann :history)] (:history old-ann))}}
   {:return-new true
    :upsert true}))

(defn find-ann-by-key [anns k]
  (first (filter #(= k (get-in % [:ann :key])) anns)))

(s/defn ^:always-validate new-token-annotation
  [db cpos :- s/Int ann :- annotation-schema]
  (let [{db :db} db
        k (get-in ann [:ann :key])
        [old-anns] (mc/find-maps db coll {:_id cpos "anns.ann.key" k})]
    (if (empty? old-anns)
      (new-annotation db coll cpos ann)
      (let [old-ann (find-ann-by-key (:anns old-anns) k)]
        (update-annotation db coll cpos ann old-ann)))))

(s/defn ^:always-validate fetch-annotation :- ann-from-db-schema
  ([db cpos :- s/Int] (fetch-annotation db cpos (inc cpos)))
  ([db cpos-from :- s/Int cpos-to :- s/Int]
   (let [{db :db} db
         out (mc/find-maps db coll {$and [{:_id {$gte cpos-from}} {:_id {$lt  cpos-to}}]})]
     (zipmap (map :_id out) out))))

(defn merge-annotations-hit
  "merge annotations retrieved for the current hit vector with hit vector"
  [hit ann-from-db]
  (map (fn [token]
         (let [id (get-token-id token)]
           (if-let [{:keys [anns]} (get ann-from-db id)]
             (assoc token :anns anns)
             token)))
       hit))

(defn find-first-id [hit]
  (first (drop-while #(neg? %) (map get-token-id hit))))

(defn merge-annotations
  "collect stored annotations for a given span of hits. Annotations are 
  collected at one for a given hit, since we know the cpos range of its
  tokens `from`: `to`"
  [db results]
  (for [{:keys [hit] :as hit-map} results
        :let [from (find-first-id hit) ;todo, find first real id (avoid dummies)
              to   (find-first-id (reverse hit))
              anns-from-db (fetch-annotation db from to)
              new-hit (merge-annotations-hit hit anns-from-db)]]
    (assoc hit-map :hit new-hit)))

;(def db (.start (new-db {:url "mongodb://127.0.0.1:27017/cleeboTest"})))
;(mc/find-maps (:db db) coll )
;(timbre/debug (fetch-annotation db 410))


