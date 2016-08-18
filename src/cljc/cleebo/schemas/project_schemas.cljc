(ns cleebo.schemas.project-schemas
  (:require [schema.core :as s]
            [schema.coerce :as coerce]
            [taoensso.timbre :as timbre]
            [cleebo.app-utils :refer [deep-merge]]
            [cleebo.schemas.user-schemas :refer [settings-schema project-history-schema]]
            [cleebo.schemas.results-schemas :refer [query-results-schema]]))

(def update-schema
  {:type s/Str
   :timestamp s/Int
   s/Any s/Any})

(def status-schema
  {:status (s/enum :ok :error)
   (s/optional-key :content) {:message s/Str (s/optional-key :code) s/Str}})

(def project-session-schema
  {:query query-results-schema
   :status (s/conditional empty? {} :else status-schema)
   :filtered-users #{s/Str}})             ;filter out annotations by other users

(def project-settings-schema
  (deep-merge settings-schema {(s/optional-key :components) {}}))

(def project-user-schema
  {:username s/Str :role s/Str})

(def project-users-schema
  [project-user-schema])

(def project-schema
  #?(:clj {:name s/Str
           :description s/Str
           :created s/Int
           :users project-users-schema
           (s/optional-key :updates) [update-schema]}
     :cljs {:name s/Str
            :description s/Str
            :created s/Int
            :users [{:username s/Str :role s/Str}]
            (s/optional-key :updates) [update-schema]
            (s/optional-key :settings) project-settings-schema
            (s/optional-key :session) project-session-schema
            (s/optional-key :history) project-history-schema}))
