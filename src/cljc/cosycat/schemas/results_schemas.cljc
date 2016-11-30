(ns cosycat.schemas.results-schemas
  (:require [schema.core :as s]
            [cosycat.schemas.user-schemas :refer [sort-opts-schema filter-opts-schema]]
            [cosycat.schemas.annotation-schemas :refer [annotation-schema]]))

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
  {(s/required-key :num) s/Int          ;index of hit in current query
   ;; optional keys 
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
  (s/conditional
   empty? {}
   :else
   {:page {:from s/Int :to s/Int}
    :query-size s/Int
    :query-str s/Str
    :query-time s/Int
    :has-next s/Bool
    :sort-opts [sort-opts-schema]
    :filter-opts [filter-opts-schema]
    :corpus s/Any}))

(def query-results-schema
  {:results-summary results-summary-schema ;info about last query
   :results (s/conditional empty? [] :else results-schema) ;current hits ids
   :results-by-id (s/conditional empty? {} :else results-by-id-schema)})
