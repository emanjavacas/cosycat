(ns cleebo.query-backends.blacklab
  (:require [cleebo.query-backends.protocols :refer [Corpus handle-query]]))

(defn bl-parse-sort-opts [sort-opts]
  (reduce (fn [acc {:keys [position attribute facet]}]
            (if (= "match" position)
              (assoc acc :criterion position :attribute (or attribute "word"))))
          {}
          sort-opts))

(deftype BlacklabCorpus [corpus-name]
  Corpus
  (query [this query-str {:keys [context from page-size] :as query-opts}]
    (handle-query
     this "/blacklab"
     {:corpus corpus-name
      :query-str (js/encodeURIComponent query-str)
      :context context
      :from from
      :size (+ from page-size)
      :route :query}))

  (query-sort [this query-str {:keys [context from page-size]} sort-opts filter-opts]
    (handle-query
     this "/blacklab"
     {:corpus corpus-name
      :context context
      :from from
      :size (+ from page-size)
      :route :sort-query
      :sort-map (bl-parse-sort-opts sort-opts)}))

  (snippet [this query-str {:keys [snippet-size] :as snippet-map} hit-id]
    (handle-query
     this "/blacklab"
     {:corpus corpus-name
      :query-str query-str
      :snippet-size snippet-size
      :route :snippet}))
  
  (handler-data [corpus data] (identity data))
  (error-handler-data [corpus data] (identity data)))

(defn make-blacklab-corpus [{:keys [corpus-name]}]
  (->BlacklabCorpus corpus-name))

;; (def new-corpus (->BlacklabCorpus "mbg-small"))
;; (query new-corpus "\"a\"" {:context 10 :from 0 :page-size 5})
