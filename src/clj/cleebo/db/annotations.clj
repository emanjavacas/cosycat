(ns cleebo.db.annotations
  (:require [monger.collection :as mc]
            [monger.operators :refer :all]
            [taoensso.timbre :as timbre]
            [schema.core :as s]
            [cleebo.utils :refer [get-token-id ->int]]
            [cleebo.components.db :refer [new-db]]
            [cleebo.shared-schemas :refer [annotation-schema]]
            [schema.coerce :as coerce]))

(def coll "annotations")
;;; todo, add corpus information

(s/defn ^:always-validate new-token-annotation
  [db cpos :- s/Int ann :- annotation-schema]
  (let [db-conn (:db db)
        {timestamp :timestamp
         username :username
         {key :key value :value} :ann} ann]
    (let [[old-anns] (mc/find-maps db-conn coll {:_id cpos "anns.ann.key" key})]
      (if (not (empty? old-anns))
        (let [old-ann (first (filter #(= key (get-in % [:ann :key])) (:anns old-anns)))]
          (println "OLD-ANN!" (doall old-anns))
          (mc/find-and-modify
           db-conn coll
           {:_id cpos "anns.ann.key" key}
           {$set {"anns.$.ann.key" key "anns.$.ann.value" value
                  "anns.$.username" username "anns.$.timestamp" timestamp
                  "anns.$.history" (concat [(dissoc old-ann :history)] (:history old-ann))}}
           {:return-new true
            :upsert true}))
        (mc/find-and-modify
         db-conn coll
         {:_id cpos}
         {$push {:anns ann}}
         {:return-new true
          :upsert true})))))

(def ann-from-db-schema
  "annotation db return either `nil` or a map from 
  `annotation id` to the stored annotations vector"
  (s/maybe  {s/Int {:anns [annotation-schema] :_id s/Int}}))

(s/defn ^:always-validate fetch-annotation :- ann-from-db-schema
  ([db cpos :- s/Int] (fetch-annotation db cpos (inc cpos)))
  ([db cpos-from :- s/Int cpos-to :- s/Int]
   (let [db-conn (:db db)
         out (mc/find-maps db-conn coll
                           {$and [{:_id {$gte cpos-from}} {:_id {$lt  cpos-to}}]})]
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

;; (def db (.start (new-db {:url "mongodb://127.0.0.1:27017/cleeboTest"})))
;; (mc/find-maps (:db db))
;; (timbre/debug (fetch-annotation db 410))

