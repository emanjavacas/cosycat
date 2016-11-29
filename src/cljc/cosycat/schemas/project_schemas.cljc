(ns cosycat.schemas.project-schemas
  (:require [schema.core :as s]
            [schema.coerce :as coerce]
            [taoensso.timbre :as timbre]
            [cosycat.app-utils :refer [deep-merge]]
            [cosycat.schemas.user-schemas :refer [settings-schema filter-opts-schema sort-opts-schema]]
            [cosycat.schemas.event-schemas :refer [event-schema event-id-schema]]
            [cosycat.schemas.results-schemas :refer [query-results-schema]]))

;;; project issues schemas
(def issue-id-schema s/Any)

(def base-issue-schema
  {:id issue-id-schema
   :by s/Str
   :type s/Str
   :timestamp s/Int
   :status (s/enum "open" "closed")
   :users (s/conditional string? (s/enum "all") :else [s/Str]) ;addressed users
   :data {s/Any s/Any}
   (s/optional-key :resolve)
   {:status (s/enum "accepted" "rejected")
    :comment s/Str
    :timestamp s/Str
    :by s/Str}
   (s/optional-key :comments)
   ;; comments are stored as objects indexed by comment id
   ;; to make comment updates atomic (see http://stackoverflow.com/questions/18573117/updating-nested-arrays-in-mongodb-via-mongo-shell/18574256#18574256)
   {s/Keyword ;; comment-id as keyword
    {:comment s/Str
     :id s/Any
     :timestamp s/Int
     :by s/Str
     (s/optional-key :deleted) s/Bool
     ;; Tree Structure with parent references
     ;; https://docs.mongodb.com/v3.0/tutorial/model-tree-structures-with-parent-references/
     ;; :children is an array of comment ids pointing to children
     ;; at normalizing time - look up roots, - sort by timestamp and recurse
     (s/optional-key :children) [s/Any]}}})

(def issue-schema
  #?(:cljs (assoc base-issue-schema (s/optional-key :meta) {s/Any s/Any})
     :clj base-issue-schema))

;;; query-metadata schemas
(def query-id-schema s/Any)

(def queries-schema                     ;metadata on previous stored queries
  {:query-data {:query-str s/Str
                :corpus s/Str
                (s/optional-key :filter-opts) [filter-opts-schema]
                (s/optional-key :sort-opts) [sort-opts-schema]}
   :id query-id-schema
   :default (s/enum "unseen" "kept" "discarded")
   :timestamp s/Int
   :creator s/Str
   :hits #?(:clj {s/Any {:timestamp s/Int
                         :hit-id s/Any
                         :hit-num s/Int
                         :by s/Str
                         :status (s/enum "discarded" "kept")}}
            :cljs {:discarded #{s/Any} :kept #{s/Any}})})

;;; project session schemas
(def status-schema
  {:status (s/enum :ok :error)
   (s/optional-key :content) {:message s/Str (s/optional-key :code) s/Str}})

(def project-session-schema
  {:query query-results-schema
   :status (s/conditional empty? {} :else status-schema)   
   :components {s/Any s/Any}
   :filtered-users #{s/Str}}) ;filter out annotations by other users

(defn make-keys-optional [schema]
  (reduce-kv (fn [m k v]
               (if (s/optional-key? k)
                 (assoc m k v)
                 (-> m (assoc (s/optional-key k) v) (dissoc k))))
             {}
             schema))

;;; project settings schemas
(def project-settings-schema (make-keys-optional settings-schema))

;;; project users schemas
(def project-user-schema
  {:username s/Str :role s/Str})

(def project-users-schema
  [project-user-schema])

;;; general schema
(def project-schema
  #?(:clj {:name s/Str
           :description s/Str
           :created s/Int
           :creator s/Str
           :users project-users-schema
           (s/optional-key :issues) [issue-schema]
           (s/optional-key :events) [event-schema]}
     :cljs {:name s/Str
            :description s/Str
            :creator s/Str
            :created s/Int
            :users [{:username s/Str :role s/Str}]
            ;; things that need to be resolved
            ;; coming from projects collection
            (s/optional-key :issues) {issue-id-schema issue-schema}
            ;; things that inform about events (new user, queryetc.)
            ;; merged from both collections users and projects
            (s/optional-key :events) {event-id-schema event-schema}            
            (s/optional-key :queries) {query-id-schema queries-schema}
            (s/optional-key :settings) project-settings-schema
            (s/optional-key :session) project-session-schema}))
