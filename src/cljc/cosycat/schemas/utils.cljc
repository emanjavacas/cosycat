(ns cosycat.schemas.utils
  (:require [schema.core :as s]))

(defn make-keys-optional [schema]
  (if (or (not (map? schema))
          ;; schema predicates (s/Int, ...) are both map? and record?
          (record? schema))
    schema
    (reduce-kv (fn [m k v]
                 (if (s/optional-key? k)
                   (assoc m k v)                 
                   (-> m
                       (dissoc k)
                       (assoc (s/optional-key k) (make-keys-optional v)))))
               {}
               schema)))
