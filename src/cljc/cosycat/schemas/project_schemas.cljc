(ns cosycat.schemas.project-schemas
  (:require [schema.core :as s]
            [schema.coerce :as coerce]
            [taoensso.timbre :as timbre]
            [cosycat.app-utils :refer [deep-merge]]
            [cosycat.schemas.utils :refer [make-keys-optional]]
            [cosycat.schemas.queries-schemas :refer [query-opts-schema review-opts-schema]]
            [cosycat.schemas.user-schemas :refer [event-schema event-id-schema settings-schema]]
            [cosycat.schemas.results-schemas :refer [query-results-schema review-results-schema]]))

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
    (s/optional-key :comment) s/Str
    :timestamp s/Int
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

(def query-hit-metadata-schema
  #?(:clj  {:timestamp s/Int
            :hit-id s/Any
            :by s/Str
            :status (s/enum "discarded" "kept" "unseen")
            :_version s/Any
            (s/optional-key :project-name) s/Any
            (s/optional-key :query-id) query-id-schema}
     :cljs {:timestamp s/Int
            :hit-id s/Any
            :by s/Str
            :status (s/enum "discarded" "kept" "unseen")
            (s/optional-key :_version) s/Any}))

(def query-annotation-schema                     ;metadata on previous stored queries
  {:query-data {:query-str s/Str
                :corpus s/Str
                (s/optional-key :filter-opts) (:filter-opts query-opts-schema)
                (s/optional-key :sort-opts) (:sort-opts query-opts-schema)}
   :id query-id-schema
   :default (s/enum "unseen" "kept" "discarded")
   :description s/Str
   :timestamp s/Int
   :creator s/Str
   (s/optional-key :hits) {s/Any query-hit-metadata-schema}})

;;; project session schemas
(def status-schema
  {:status (s/enum :ok :error)
   (s/optional-key :content) {:message s/Str (s/optional-key :code) s/Str}})

(def project-session-components-schema
  {:panel-open {:query-frame s/Bool :annotation-frame s/Bool}
   :active-project-frame (s/enum :users :events :issues :queries)   
   :issue-filters {:status (s/enum "open" "closed" "all") :type s/Any}
   :event-filters {:type s/Any}
   :open-hits #{s/Any}
   :review-input-open? {:key s/Bool :value s/Bool}
   :toggle-hits (s/enum "none" "kept" "discarded" "unseen")
   :token-field s/Keyword
   (s/optional-key :issues) {s/Str {:show-hit s/Bool}}
   (s/optional-key :active-query) s/Str})

(def subhit-schema
  [{:word s/Str
    (s/optional-key :match) s/Bool
    s/Any s/Str}])

(def snippet-schema
  {:hit-id s/Str
   :snippet {:left subhit-schema :match subhit-schema :right subhit-schema}})

(def project-session-schema
  {:query {:results query-results-schema}
   :review {:results review-results-schema :query-opts review-opts-schema}
   :snippet (s/conditional empty? {} :else snippet-schema)
   :status (s/conditional empty? {} :else status-schema)   
   :components project-session-components-schema
   :filtered-users #{s/Str}}) ;filter out annotations by other users

;;; project settings schemas
(def project-settings-schema (make-keys-optional settings-schema))

;; (identity settings-schema)
;; (make-keys-optional settings-schema)

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
           (s/optional-key :queries) {query-id-schema query-annotation-schema}
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
            (s/optional-key :queries) {query-id-schema query-annotation-schema}
            (s/optional-key :settings) project-settings-schema
            (s/optional-key :session) project-session-schema}))
