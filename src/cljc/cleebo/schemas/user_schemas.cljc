(ns cleebo.schemas.user-schemas
  (:require [schema.core :as s]
            [cleebo.schemas.project-schemas :refer [project-schema]]))

(def avatar-schema
  {:href s/Str
   :dominant-color s/Str})

(def user-schema
  {:username s/Str
   :firstname s/Str
   :lastname s/Str
   :email s/Str
   :avatar avatar-schema
   :roles #{s/Str}
   :created s/Int
   :last-active s/Int
   :projects [project-schema]})
