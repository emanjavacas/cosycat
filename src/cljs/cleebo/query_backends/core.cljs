(ns cleebo.query-backends.core
  (:require [cleebo.query-backends.blacklab :refer [make-blacklab-corpus]]
            [cleebo.query-backends.blacklab-server :refer [make-blacklab-server-corpus]]))

(def ctors
  {:blacklab make-blacklab-corpus
   :blacklab-server make-blacklab-server-corpus})

(defn get-corpus [mem corpus-name corpus-type args]
  (let [ctor (get ctors corpus-type)]
    (if-let [corpus (get @mem corpus-name)]      
      corpus
      (ctor args))))

(let [mem (atom {})]
  (defn ensure-corpus
    "instantiates corpus object and caches it for further calls"
    [{:keys [name type args] :as corpus-config} & {:keys [force] :or {force false}}]
    (if-let [corpus (get-corpus mem name type args)]
      (do (-> (swap! mem assoc name corpus) (get name)))
      (throw (js/Error. "Corpus not available")))))


