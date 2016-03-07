(ns cleebo.db.annotations
  (:require [monger.collection :as mc]
            [monger.operators :refer :all]
            [taoensso.timbre :as timbre]
            [schema.core :as s]
            [cleebo.db.component :refer [ new-db]]
            [cleebo.shared-schemas :refer
             [annotation-schema ->span-ann]]
            [schema.coerce :as coerce]))

(def coll "annotations")

(s/defn ^:always-validate new-token-annotation
  [db cpos :- s/Int ann :- annotation-schema]
  (let [db-conn (:db db)]
    (mc/update db-conn coll {:_id cpos} {$push {:anns ann}} {:upsert true})))

(s/defn ^:always-validate new-span-annotation
  [db from :- s/Int to :- s/Int ann :- annotation-schema]
  (let [db-conn (:db db)]
    (doseq [cpos (range from to)]
      (let [ann-doc (cond
                      (= cpos from) (->span-ann "B" ann)
                      (= cpos to)   (->span-ann "O" ann)
                      :else         (->span-ann "i" ann))]
        (mc/update db-conn coll {:_id cpos} {$push {:anns ann-doc}} {:upsert true})))))

(s/defn ^:always-validate fetch-annotation
   :- (s/maybe  {s/Int {:anns [annotation-schema] :_id s/Int}})
  ([db cpos :- s/Int]
   (let [db-conn (:db db)
         out (mc/find-maps db-conn coll {:_id cpos})]
     (zipmap (map :_id out) out)))
  ([db cpos-from :- s/Int cpos-to :- s/Int]
   :- (s/maybe  {s/Int {:anns [annotation-schema] :_id s/Int}})
   (let [db-conn (:db db)
         out (mc/find-maps
              db-conn
              coll
              {$and [{:_id {$gte cpos-from}}
                     {:_id {$lt  cpos-to}}]})]
     (zipmap (map :_id out) out))))

;(def db (.start (new-db {:url "mongodb://127.0.0.1:27017/cleeboTest"})))

;(mc/find-maps (:db db))
;(timbre/debug (fetch-annotation db 410))

