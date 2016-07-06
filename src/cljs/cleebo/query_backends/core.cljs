(ns cleebo.query-backends.core
  (:require [cleebo.query-backends.blacklab :refer [make-blacklab-corpus]]
            [cleebo.query-backends.blacklab-server :refer [make-blacklab-server-corpus]]))

(def ctors
  {:blacklab make-blacklab-corpus
   :blacklab-server make-blacklab-server-corpus})

(defn instantiate-corpus [corpus-type args-map]
  (if-let [ctor (get ctors corpus-type)]
    (ctor args-map)
    (throw (js/Error. (str "Couldn't find corpus type" corpus-type)))))

(defn maybe-get-corpus [mem corpus-name corpus-type args-map]
  (if-let [corpus (get @mem corpus-name)]      
    corpus
    (instantiate-corpus corpus-type args-map)))

(let [mem (atom {})]
  (defn ensure-corpus
    "instantiates corpus object and caches it for further calls"
    [{:keys [name type args] :as corpus-config} & {:keys [force] :or {force false}}]
    (->> (if force
           (instantiate-corpus type args)
           (maybe-get-corpus mem name type args))
         (swap! mem assoc name)
         first
         val)))


