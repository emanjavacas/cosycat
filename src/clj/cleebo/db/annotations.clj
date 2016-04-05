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

(defn find-ann-by-key [anns k]
  (first (filter #(= k (get-in % [:ann :key])) anns)))

(defn update-ann-history [ann]
  (concat [(dissoc ann :history)] (:history ann)))

(defn- create-annotation
  "inserts annotation for a token position without any previous annotations"
  [db coll token-id ann]
  (mc/find-and-modify
   db coll
   {:_id token-id}
   {$push {:anns ann}}
   {:return-new true
    :upsert true}))

(defn- update-annotation
  "inserts annotation for a token position with previous
  annotations (updating its history)"
  [db coll token-id
   {timestamp :timestamp
    username :username
    {k :key v :value} :ann :as ann}
   old-ann]
  (mc/find-and-modify
   db coll
   {:_id token-id "anns.ann.key" k}
   {$set {"anns.$.ann.key" k "anns.$.ann.value" v
          "anns.$.username" username "anns.$.timestamp" timestamp
          "anns.$.history" (update-ann-history old-ann)}}
   {:return-new true
    :upsert true}))

;;; [token-id ann hit-id]
;;; [int      map int   ] normal situation; single ann
;;; [vec      map vec   ] same ann for multiple tokens (doesn't imply mult hit-ids: spans)
;;; [vec      vec vec   ] mult anns for mult tokens (implies mult hit-ids)

(defmulti new-token-annotation
  "dispatch based on type (either a particular value or 
  a vector of that value for bulk annotations)"
  (fn [db token-id ann]
    [(type token-id) (type ann)]))

(s/defmethod ^:always-validate new-token-annotation
  [java.lang.Long clojure.lang.PersistentArrayMap]
  [db token-id :- s/Int ann :- annotation-schema]
  (let [{db :db} db
        k (get-in ann [:ann :key])
        [old-anns] (mc/find-maps db coll {:_id token-id "anns.ann.key" k})]
    (try (let [{:keys [anns _id]} (if (empty? old-anns) ;no anns found for token position
                                    (create-annotation db coll token-id ann)
                                    (let [old-ann (find-ann-by-key (:anns old-anns) k)]
                                      (update-annotation db coll token-id ann old-ann)))]
        {:data {:token-id token-id :anns anns}
         :status :ok
         :type :annotation})
      (catch Exception e
        {:data {:token-id token-id
                :reason :internal-error
                :e (str e)}
         :status :error
         :type :annotation}))))

(s/defmethod ^:always-validate new-token-annotation
  [clojure.lang.PersistentVector clojure.lang.PersistentVector]
  [db token-ids :- [s/Int] anns :- [annotation-schema]]
  (vec (doall (for [[token-id ann] (map vector token-ids anns)]
                (new-token-annotation db token-id ann)))))

(s/defn ^:always-validate fetch-annotation :- ann-from-db-schema
  ([db token-id :- s/Int] (fetch-annotation db token-id (inc token-id)))
  ([db id-from :- s/Int id-to :- s/Int]
   (let [{db :db} db
         out (mc/find-maps db coll {$and [{:_id {$gte id-from}} {:_id {$lt id-to}}]})]
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
  collected at one for a given hit, since we know the token-id range of its
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
