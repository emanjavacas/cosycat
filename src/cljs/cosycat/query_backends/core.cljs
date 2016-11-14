(ns cosycat.query-backends.core
  (:require [cosycat.query-backends.blacklab :refer [make-blacklab-corpus]]
            [cosycat.query-backends.blacklab-server :refer [make-blacklab-server-corpus]]
            [cosycat.query-backends.protocols :refer [corpus-info]]))

(def ctors
  {:blacklab make-blacklab-corpus
   :blacklab-server make-blacklab-server-corpus})

(defn get-corpus [mem corpus-name corpus-type args]
  (let [ctor (get ctors corpus-type)]
    (if-let [corpus (get @mem corpus-name)]      
      corpus
      (let [corpus (ctor corpus-name args)]
        (corpus-info corpus)
        corpus))))

(let [mem (atom {})]
  (defn ensure-corpus
    "instantiates corpus object and caches it for further calls"
    [{corpus-name :corpus corpus-type :type args :args :as corpus-config} &
     {:keys [force] :or {force false}}]
    (if-let [corpus (get-corpus mem corpus-name corpus-type args)]
      (do (-> (swap! mem assoc corpus-name corpus) (get corpus-name)))
      (throw (js/Error. "Corpus not available")))))


