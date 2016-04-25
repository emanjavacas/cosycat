(ns cleebo.schemas.route-schemas
  (:require [schema.core :as s]
            [schema.coerce :as coerce]
            [cleebo.schemas.annotation-schemas
             :refer [annotation-schema span-schema]]
            [cleebo.schemas.project-schemas :refer [project-schema]]
            [cleebo.schemas.app-state-schemas
             :refer [public-user-schema avatar-schema]]
            [cleebo.app-utils :refer [deep-merge]]
            [taoensso.timbre :as timbre]))

(def blueprint-from-server
  {:status s/Keyword
   :type   s/Keyword
   :source s/Str
   (s/optional-key :payload-id) s/Any}) ;it only applies to echo-channels

(def ann-ok-from-server-schema
  "multiple anns implies multiple hit-ids"
  (merge blueprint-from-server
         {:data {:hit-id  (s/if vector? [s/Int] s/Int)
                 :ann-map (s/if vector? [annotation-schema] annotation-schema)}}))

(def ann-error-from-server-schema
  (merge blueprint-from-server
         {:data {:span (s/if vector? [span-schema] span-schema)
                 :hit-id (s/if vector? [s/Int] s/Int)
                 :reason   s/Keyword
                 (s/optional-key :e) s/Str
                 (s/optional-key :username) s/Str}}))

(def notify-blueprint
  (deep-merge blueprint-from-server
              {:data {(s/optional-key :by) s/Str}}))

(def notify-info-from-server-schema
  (deep-merge notify-blueprint
              {:data {:message s/Str}}))

(def notify-login-from-server-schema
  (deep-merge notify-blueprint
              {:data public-user-schema}))

(def notify-logout-from-server-schema
  (deep-merge notify-blueprint
              {:data {:username s/Str}}))

(def notify-new-project-from-server-schema
  (deep-merge notify-blueprint
              {:data project-schema}))

(def notify-new-user-avatar-from-server-schema
  (deep-merge notify-blueprint
              {:data {:avatar avatar-schema :username s/Str}}))

(defn ws-from-server
  [{:keys [type status data] :as payload}]
  (case [type status]
    [:annotation :ok]    ann-ok-from-server-schema
    [:annotation :error] ann-error-from-server-schema
    [:notify     :info]  notify-info-from-server-schema
    [:notify     :login] notify-login-from-server-schema
    [:notify     :logout] notify-logout-from-server-schema
    [:notify     :signup] notify-login-from-server-schema
    [:notify     :new-project] notify-new-project-from-server-schema
    [:notify     :new-user-avatar] notify-new-user-avatar-from-server-schema))

(defn ws-from-client
  [{:keys [type data] :as payload}]
  (case type
    :annotation {:type s/Keyword
                 :data {:hit-id  (s/if vector? [s/Int]   s/Int)
                        :ann-map (s/if vector? [annotation-schema] annotation-schema)}
                 :payload-id s/Any
                 (s/optional-key :status) s/Any}
    :notify     {:type s/Keyword
                 :data {s/Any s/Any}
                 (s/optional-key :payload-id) s/Any}))
