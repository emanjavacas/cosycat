(ns cosycat.schemas.event-schemas
  (:require [schema.core :as s]))

(def event-schema
  [{:timestamp s/Int
    :type s/Keyword
    :data {s/Any s/Any}}])
