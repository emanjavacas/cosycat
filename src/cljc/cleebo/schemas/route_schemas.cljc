(ns cleebo.schemas.route-schemas
  (:require [schema.core :as s]
            [schema.coerce :as coerce]
            [cleebo.schemas.annotation-schemas :refer [annotation-schema span-schema cpos-schema]]
            [cleebo.schemas.project-schemas
             :refer [project-schema update-schema project-user-schema]]
            [cleebo.schemas.user-schemas :refer [avatar-schema]]
            [cleebo.schemas.app-state-schemas :refer [public-user-schema]]
            [cleebo.schemas.results-schemas :refer [hit-id-schema]]
            [cleebo.app-utils :refer [deep-merge]]
            [taoensso.timbre :as timbre]))

(def blueprint-from-server
  {:type   s/Keyword
   :data {(s/optional-key :by) s/Str}})

(def ann-from-server-schema
  "multiple anns implies multiple hit-ids"
  (deep-merge blueprint-from-server
              {:data {:hit-id  (s/if vector? [hit-id-schema] hit-id-schema)
                      :anns {cpos-schema {s/Str annotation-schema}}
                      :project s/Str}}))

(def ann-error-from-server-schema
  (deep-merge blueprint-from-server
              {:data {(s/required-key :span) (s/if vector? [span-schema] span-schema)
                      (s/required-key :hit-id) (s/if vector? [hit-id-schema] hit-id-schema)}}))

(def info-from-server-schema
  (deep-merge blueprint-from-server {:data {:message s/Str}}))

(def login-from-server-schema
  (deep-merge blueprint-from-server {:data public-user-schema}))

(def logout-from-server-schema
  (deep-merge blueprint-from-server {:data {:username s/Str}}))

(def new-project-from-server-schema
  (deep-merge blueprint-from-server {:data {:project project-schema}}))

(def project-remove-from-server-schema
  (deep-merge blueprint-from-server {:data {:project-name s/Str}}))

(def new-user-avatar-from-server-schema
  (deep-merge blueprint-from-server {:data {:avatar avatar-schema :username s/Str}}))

(def project-update-from-server-schema
  (deep-merge blueprint-from-server {:data update-schema}))

(def project-new-user-from-server-schema
  (deep-merge blueprint-from-server {:data {:user project-user-schema :project-name s/Str}}))

(def project-add-user-from-server-schema
  (deep-merge blueprint-from-server {:data {:project project-schema}}))

(def project-remove-user-from-server-schema
  (deep-merge blueprint-from-server {:data nil}))

(defn ws-from-server
  [{:keys [type] :as payload}]
  (case type
    :annotation          ann-from-server-schema
    :info                info-from-server-schema
    :login               login-from-server-schema
    :logout              logout-from-server-schema
    :signup              login-from-server-schema
    :new-project         new-project-from-server-schema
    :project-remove      project-remove-from-server-schema
    :project-update      project-update-from-server-schema
    :project-add-user    project-add-user-from-server-schema
    :project-new-user    project-new-user-from-server-schema
    :project-remove-user project-remove-user-from-server-schema
    :new-user-avatar     new-user-avatar-from-server-schema))

(defn ws-from-client
  [{:keys [type data] :as payload}]
  (case type
    :annotation {:type s/Keyword
                 :data {:hit-id  (s/if vector? [hit-id-schema] hit-id-schema)
                        :ann-map (s/if vector? [annotation-schema] annotation-schema)}
                 :payload-id s/Any
                 (s/optional-key :status) s/Any}
    :notify     {:type s/Keyword
                 :data {s/Any s/Any}
                 (s/optional-key :payload-id) s/Any}))
