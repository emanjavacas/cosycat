(ns cleebo.schemas.route-schemas
  (:require [schema.core :as s]
            [schema.coerce :as coerce]
            [cleebo.schemas.annotation-schemas :refer [annotation-schema span-schema]]
            [cleebo.schemas.project-schemas :refer [project-schema]]
            [cleebo.schemas.user-schemas :refer [avatar-schema]]
            [cleebo.schemas.app-state-schemas :refer [public-user-schema hit-id-schema]]
            [cleebo.app-utils :refer [deep-merge]]
            [taoensso.timbre :as timbre]))

(def blueprint-from-server
  {:type   s/Keyword
   :data {(s/optional-key :by) s/Str}})

(def ann-from-server-schema
  "multiple anns implies multiple hit-ids"
  (deep-merge blueprint-from-server
              {:data {:hit-id  (s/if vector? [hit-id-schema] hit-id-schema)
                      :ann-map (s/if vector? [annotation-schema] annotation-schema)}}))

(def ann-error-from-server-schema
  (deep-merge blueprint-from-server
              {:data {:span (s/if vector? [span-schema] span-schema)
                      :hit-id (s/if vector? [hit-id-schema] hit-id-schema)
                      :reason   s/Keyword
                      (s/optional-key :e) s/Str
                      (s/optional-key :username) s/Str}}))

(def info-from-server-schema
  (deep-merge blueprint-from-server
              {:data {:message s/Str}}))

(def login-from-server-schema
  (deep-merge blueprint-from-server
              {:data public-user-schema}))

(def logout-from-server-schema
  (deep-merge blueprint-from-server
              {:data {:username s/Str}}))

(def new-project-from-server-schema
  (deep-merge blueprint-from-server
              {:data project-schema}))

(def new-user-avatar-from-server-schema
  (deep-merge blueprint-from-server
              {:data {:avatar avatar-schema :username s/Str}}))

(defn ws-from-server
  [{:keys [type] :as payload}]
  (case type
    :annotation      ann-from-server-schema
    :info            info-from-server-schema
    :login           login-from-server-schema
    :logout          logout-from-server-schema
    :signup          login-from-server-schema
    :new-project     new-project-from-server-schema
    :new-user-avatar new-user-avatar-from-server-schema))

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
