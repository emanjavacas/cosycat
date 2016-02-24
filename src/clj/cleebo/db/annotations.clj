(ns cleebo.db.annotations
  (:require [monger.collection :as mc]
            [monger.operators :refer :all]
            [schema.core :as s]
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

(s/defn ^:always-validate fetch-annotation :- (s/maybe [annotation-schema])
  [db cpos :- s/Int]
  (let [db-conn (:db db)]
    (-> (mc/find-one-as-map db-conn coll {:_id cpos})
        :anns)))
