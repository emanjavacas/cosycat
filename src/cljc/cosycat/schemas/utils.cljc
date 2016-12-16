(ns cosycat.schemas.utils
  (:require [schema.core :as s]))

(defn make-keys-optional [schema]
  (reduce-kv (fn [m k v]
               (if (s/optional-key? k)
                 (assoc m k v)
                 (-> m (assoc (s/optional-key k) v) (dissoc k))))
             {}
             schema))
