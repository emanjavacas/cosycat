(ns cosycat.schemas.project-schemas
  (:require [schema.core :as s]
            [schema.coerce :as coerce]
            [taoensso.timbre :as timbre]
            [cosycat.app-utils :refer [deep-merge]]
            [cosycat.schemas.user-schemas :refer [settings-schema project-history-schema]]
            [cosycat.schemas.results-schemas :refer [query-results-schema]]))

(def issue-schema
  {:type s/Str
   :timestamp s/Int
   :status (s/enum "open" "closed")
   :users [s/Str]
   s/Any s/Any})

(def status-schema
  {:status (s/enum :ok :error)
   (s/optional-key :content) {:message s/Str (s/optional-key :code) s/Str}})

(def project-session-schema
  {:query query-results-schema
   :status (s/conditional empty? {} :else status-schema)
   :components {s/Any s/Any}
   :filtered-users #{s/Str}})             ;filter out annotations by other users

(defn make-keys-optional [schema]
  (reduce-kv (fn [m k v]
               (if (s/optional-key? k)
                 (assoc m k v)
                 (-> m (assoc (s/optional-key k) v) (dissoc k))))
             {}
             schema))

(def project-settings-schema (make-keys-optional settings-schema))

(def project-user-schema
  {:username s/Str :role s/Str})

(def project-users-schema
  [project-user-schema])

(def project-schema
  #?(:clj {:name s/Str
           :description s/Str
           :created s/Int
           :creator s/Str
           :users project-users-schema
           (s/optional-key :issues) [issue-schema]}
     :cljs {:name s/Str
            :description s/Str
            :creator s/Str
            :created s/Int
            :users [{:username s/Str :role s/Str}]
            (s/optional-key :issues) [issue-schema]
            (s/optional-key :settings) project-settings-schema
            (s/optional-key :session) project-session-schema
            (s/optional-key :history) project-history-schema}))
