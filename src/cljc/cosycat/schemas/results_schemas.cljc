(ns cosycat.schemas.results-schemas
  (:require [schema.core :as s]
            [cosycat.schemas.utils :refer [make-keys-optional]]
            [cosycat.schemas.queries-schemas :refer [review-opts-schema query-opts-schema]]
            [cosycat.schemas.annotation-schemas :refer [annotation-schema]]))

;;; Query
;;; hit/token schemas
(def hit-token-schema
  {;; required keys
   (s/required-key :word)   s/Str
   (s/required-key :id)     s/Any
   ;; optional keys
   (s/optional-key :marked) s/Bool      ;is token marked for annotation?
   (s/optional-key :anns)   {s/Str annotation-schema} ;ann-key to ann-map
   ;; any other additional keys
   s/Keyword                s/Any})

(def hit-id-schema s/Any)

(def hit-meta-schema
  {;; optional keys
   (s/optional-key :num) s/Int          ;index of hit in current query
   (s/optional-key :marked) s/Bool      ;is hit marked for annotation?
   (s/optional-key :has-marked) s/Bool  ;does hit contain marked tokens?
   (s/optional-key :query-str) s/Str    ;the query-str that retrieved it
   ;; any other additional keys
   s/Keyword                s/Any})

(def results-by-id-schema
  "Internal representation of results. A map from ids to hit-maps"
  {hit-id-schema {:hit  [hit-token-schema]
                  :id s/Any ;hit-id used to quickly identify ann updates
                  :meta hit-meta-schema}})

(def results-schema
  "Current results being displayed are represented as an ordered list
  of hits ids. Each `id` map to an entry in the :results-by-id map"
  [hit-id-schema])                             ;use hit-num instead?

(def results-summary-schema
  {:page {:from s/Int :to s/Int}
   :query-size s/Int
   :query-str s/Str
   :query-time s/Int
   :has-next s/Bool
   :sort-opts (:sort-opts query-opts-schema)
   :filter-opts (:filter-opts query-opts-schema)
   :corpus s/Any})

(def query-results-schema
  {:results-summary (s/conditional empty? {} :else results-summary-schema)
   :results (s/conditional empty? [] :else results-schema)
   :results-by-id (s/conditional empty? {} :else results-by-id-schema)})

;;; Review
(def grouped-data-schema
  {s/Str {:hit-id s/Str
          :corpus s/Str
          :anns [annotation-schema]}})

(def review-results-summary-schema
  {:page {:page-num s/Int
          :page-size s/Int ;; returned page-size
          :num-hits s/Int}
   :query-size s/Int ;; number of annotations returned
   :size s/Int ;; selected page-size
   :context s/Int ;; number of context tokens
   :query-map (make-keys-optional (:query-map review-opts-schema))})

(def review-results-schema
  {:results-by-id (s/conditional empty? {} :else results-by-id-schema)
   :results-summary (s/conditional empty? {} :else review-results-summary-schema)})

