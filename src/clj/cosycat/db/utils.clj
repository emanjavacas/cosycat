(ns cosycat.db.utils
  (:require [cosycat.app-utils :refer [dekeyword]]))

(defn ->set-update-map
  "transforms a db update-map into a proper mongodb update document to be passed as value of $set"
  [prefix update-map]
  (reduce-kv (fn [m k v]
               (-> m
                   (assoc (str prefix "." (dekeyword k)) v)
                   (dissoc k)))
             {}
             update-map))
