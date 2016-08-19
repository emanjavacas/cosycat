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

(defn make-schema [m]
  (deep-merge blueprint-from-server m))

(def ann-error-from-server-schema
  (make-schema {:data {:span (s/if vector? [span-schema] span-schema)
                       :hit-id (s/if vector? [hit-id-schema] hit-id-schema)}}))

(defn ws-from-server
  [{:keys [type] :as payload}]
  (case type
    :annotation (make-schema {:data {:hit-id (s/if vector? [hit-id-schema] hit-id-schema)
                                     :anns {cpos-schema {s/Str annotation-schema}}
                                     :project s/Str}})
    :info (make-schema {:data {:message s/Str}})
    :login (make-schema {:data public-user-schema})
    :logout (make-schema {:data {:username s/Str}})
    :signup (make-schema {:data public-user-schema})
    :new-project (make-schema {:data {:project project-schema}})
    :project-remove (make-schema {:data {:project-name s/Str}})
    :project-update (make-schema {:data update-schema})
    :project-add-user (make-schema {:data {:project project-schema}})
    :project-new-user (make-schema {:data {:user project-user-schema :project-name s/Str}})
    :project-remove-user (make-schema {:data nil})
    :new-user-avatar (make-schema {:data {:avatar avatar-schema :username s/Str}})))

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
