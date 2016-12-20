(ns cosycat.schemas.queries-schemas
  (:require [schema.core :as s]))

;;; Query opts
(def filter-opts-schema
  {:attribute s/Str :value s/Str})

(def sort-opts-schema
  {:position s/Str :attribute s/Str :facet s/Str})

(def review-sort-opts-schema
  {:attribute (s/enum [:ann :key] [:ann :value] :corpus :username :timestamp)
   :direction (s/enum :ascending :descending)})

(def query-opts-schema
  {:corpus s/Str
   :query-opts {:context s/Int :from s/Int :page-size s/Int} ;from is kept updated
   :sort-opts [sort-opts-schema]
   :filter-opts [filter-opts-schema]
   :snippet-opts {:snippet-size s/Int :snippet-delta s/Int}})

;;; Review opts
(def review-opts-schema
  {:context s/Int ;; number of context tokens
   :size s/Int ;; page-size
   :window s/Int ;; number of tokens around context
   :query-map {:ann {(s/optional-key :key) s/Str (s/optional-key :value) s/Str}
               :corpus #{s/Str}
               :username #{s/Str}
               :timestamp {(s/optional-key :from) s/Int (s/optional-key :to) s/Int}}
   :sort-opts [sort-opts-schema]})
