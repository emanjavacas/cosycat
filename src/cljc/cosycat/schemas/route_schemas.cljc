(ns cosycat.schemas.route-schemas
  (:require [schema.core :as s]
            [schema.coerce :as coerce]
            [cosycat.schemas.annotation-schemas
             :refer [annotation-schema span-schema cpos-schema token-id-schema ann-key-schema]]
            [cosycat.schemas.project-schemas
             :refer [project-schema issue-schema project-user-schema queries-schema]]
            [cosycat.schemas.user-schemas :refer [avatar-schema]]
            [cosycat.schemas.app-state-schemas :refer [public-user-schema]]
            [cosycat.schemas.results-schemas :refer [hit-id-schema]]
            [cosycat.app-utils :refer [deep-merge]]
            [taoensso.timbre :as timbre]))

(def blueprint-from-server
  {:type   s/Keyword
   :data {s/Any s/Any}
   (s/optional-key :by) s/Str})

(defn make-schema [m]
  (deep-merge blueprint-from-server m))

(def ann-error-from-server-schema
  (make-schema {:data {:span (s/if vector? [span-schema] span-schema)
                       :hit-id (s/if vector? [hit-id-schema] hit-id-schema)}}))

(def annotation-route-schema
  (make-schema {:data {:hit-id (s/if vector? [hit-id-schema] hit-id-schema)
                       :anns {token-id-schema {s/Str annotation-schema}}
                       :project s/Str}}))

(def remove-annotation-route-schema
  (make-schema {:data {:key ann-key-schema
                       :span span-schema
                       :project s/Str
                       :hit-id hit-id-schema}}))

(defn ws-from-server
  "validator function for server-sent ws-data dispatching on payload `:type` field"
  [{:keys [type] :as payload}]
  (case type
    ;; Annotations
    :annotation annotation-route-schema
    :remove-annotation remove-annotation-route-schema
    ;; Auth
    :info (make-schema {:data {:message s/Str}})
    :login (make-schema {:data public-user-schema})
    :logout (make-schema {:data {:username s/Str}})
    :signup (make-schema {:data public-user-schema})
    ;; Projects
    ;; Projects general
    :new-project (make-schema {:data {:project project-schema}})
    :remove-project (make-schema {:data {:project-name s/Str}})
    ;; Projects issues
    :new-project-issue (make-schema {:data {:project-name s/Str :issue issue-schema}})
    :update-project-issue (make-schema {:data {:project-name s/Str :issue issue-schema}})
    ;; :close-project-issue (make-schame {:data {:project-name s/Str}})
    ;; Projects users
    :add-project-user (make-schema {:data {:project project-schema}})
    :new-project-user (make-schema {:data {:user project-user-schema :project-name s/Str}})
    :remove-project-user (make-schema {:data {:username s/Str :project-name s/Str}})
    :new-project-user-role (make-schema {:data {:project-name s/Str :username s/Str :role s/Str}})
    ;; Projects queries
    :new-query-metadata (make-schema {:data {:query queries-schema :project-name s/Str}})
    :update-query-metadata (make-schema {:data {:id s/Str
                                                :hit-id s/Str
                                                :status (s/enum "kept" "discarded" "unseen")
                                                :project-name s/Str}})
    :drop-query-metadata (make-schema {:data {:id s/Str :project-name s/Str}})
    ;; Users
    :new-user-avatar (make-schema {:data {:avatar avatar-schema :username s/Str}})
    :new-user-info (make-schema {:data {:update-map {s/Keyword s/Any} :username s/Str}})))

(defn ws-from-client
  "validator function for client-sent ws-data dispatching on payload `:type` field"
  [{:keys [type data] :as payload}]
  (case type
    :notify {:type s/Keyword
             :data {s/Any s/Any}
             (s/optional-key :payload-id) s/Any}))
