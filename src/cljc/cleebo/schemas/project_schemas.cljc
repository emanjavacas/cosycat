(ns cleebo.schemas.project-schemas
  (:require [schema.core :as s]
            [schema.coerce :as coerce]
            [taoensso.timbre :as timbre]
            #?(:clj [clojure.core.match :refer [match]]
               :cljs [cljs.core.match :refer-macros [match]])))

(def update-schema
  [{s/Any s/Any}])

(def project-schema
  {:name s/Str
   :description s/Str
   :created s/Int
   :creator s/Str
   (s/optional-key :users)   [{:username s/Str :role s/Str}]
   (s/optional-key :updates) [update-schema]})

