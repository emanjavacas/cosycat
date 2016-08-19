(ns cleebo.schemas.annotation-schemas
  (:require [schema.core :as s]
            [schema.coerce :as coerce]
            [taoensso.timbre :as timbre]))

(def cpos-schema s/Int)

(def token-span-schema
  {:type (s/enum "token")
   :scope cpos-schema})

(def iob-span-schema
  {:type (s/enum "IOB")
   :scope {:B cpos-schema :O cpos-schema}})

(def span-schema
  (s/conditional #(= (:type %) "token") token-span-schema
                 #(= (:type %) "IOB")   iob-span-schema))

(def history-schema
  [{:ann {:key s/Str :value s/Str}
    :username s/Str
    :timestamp s/Int
    :corpus s/Str
    :query s/Str
    :hit-id s/Any
    :_version s/Int
    :span span-schema}])

(def annotation-schema
  {:ann {:key s/Str :value s/Str}
   #?(:clj :username :cljs (s/optional-key :username)) s/Str ;we don't send username along
   :timestamp s/Int
   :span span-schema
   :corpus s/Str
   :query s/Str
   :hit-id s/Any
   #?(:clj :_id :cljs (s/optional-key :_id)) s/Any ;outgoing annotations do not have an id yet
   #?(:clj :_version :cljs (s/optional-key :_version)) s/Any   
   (s/optional-key :history) history-schema}) ;     this is the same except history and _id
