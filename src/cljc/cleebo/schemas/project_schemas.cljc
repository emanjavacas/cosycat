(ns cleebo.schemas.project-schemas
  (:require [schema.core :as s]
            [schema.coerce :as coerce]
            [taoensso.timbre :as timbre]
            [cleebo.schemas.user-schemas :refer [settings-schema]]))

(def update-schema
  [{s/Any s/Any}])

(def project-schema
  {:name s/Str
   :description s/Str
   :created s/Int
   :creator s/Str
   (s/optional-key :settings) settings-schema
   (s/optional-key :users)   [{:username s/Str :role s/Str}]
   (s/optional-key :updates) [update-schema]})

