(ns cosycat.schemas.event-schemas
  (:require [schema.core :as s]))

(def event-id-schema s/Any)

(def event-schema
  {:id event-id-schema
   :timestamp s/Int
   (s/optional-key :repeated) [s/Int] ;field to efficiently collapse repeated events
   :type s/Keyword
   :data {s/Any s/Any}})
