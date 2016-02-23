(ns cleebo.db.annotations
  (:require [monger.collection :as mc]
            [monger.operators :refer :all]
            [schema.core :as s]
            [schema.coerce :as coerce]))

(def annotation-schema
  {s/Str {s/Str s/Str
          :time s/Int}})

(defn new-token-annotation [db cpos user ann]
  {:pre [(integer? cpos)]}
  (let [db-conn (:db db)
        coll "annotations"]
    (mc/save db-conn coll (merge {:_id cpos} {user ann}))))

(defmulti make-span-ann (fn [IOB ann] IOB))
(defmethod make-span-ann ["I" :I :i] [ann] {:span {:IOB "I" :ann ann}})
(defmethod make-span-ann ["B" :B :b] [ann] {:span {:IOB "B" :ann ann}})
(defmethod make-span-ann ["O" :O :o] [ann] {:span {:IOB "O" :ann ann}})

(defn new-span-annotation [db from to user ann]
  {:pre [(and (integer? from) (integer? to))]}
  (let [db-conn (:db db)
        coll "annotations"]
    (doseq [cpos (range from to)]
      (cond
        (= cpos from) (mc/save db-conn coll (merge {:_id cpos} {user (make-span-ann :B ann)}))
        (= cpos to)   (mc/save db-conn coll (merge {:_id cpos} {user (make-span-ann :O ann)}))
        :else         (mc/save db-conn coll (merge {:_id cpos} {user (make-span-ann :I ann)}))))))
