(ns cleebo.db.annotations
  (:require [monger.collection :as mc]
            [monger.operators :refer :all]
            [taoensso.timbre :as timbre]
            [schema.core :as s]
            [cleebo.utils :refer [get-token-id ->int]]
            [cleebo.db.component :refer [new-db]]
            [cleebo.shared-schemas :refer [annotation-schema]]
            [schema.coerce :as coerce]))

(def coll "annotations")
;;; todo, add corpus information

(s/defn ^:always-validate new-token-annotation
  [db cpos :- s/Int ann :- annotation-schema]
  (let [db-conn (:db db)]
    (mc/update db-conn coll {:_id cpos} {$push {:anns ann}} {:upsert true})))

;;; must go to the client
;; (s/defn ^:always-validate new-span-annotation
;;   [db from :- s/Int to :- s/Int ann :- annotation-schema]
;;   (let [db-conn (:db db)]
;;     (doseq [cpos (range from to)]
;;       (let [ann-doc (cond
;;                       (= cpos from) (->span-ann "B" ann)
;;                       (= cpos to)   (->span-ann "O" ann)
;;                       :else         (->span-ann "i" ann))]
;;         (mc/update db-conn coll {:_id cpos} {$push {:anns ann-doc}} {:upsert true})))))

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

;(def db (.start (new-db {:url "mongodb://127.0.0.1:27017/cleeboTest"})))
;(mc/find-maps (:db db))
;(timbre/debug (fetch-annotation db 410))

