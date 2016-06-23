(ns cleebo.schemas.app-state-schemas
  (:require [schema.core :as s]
            [schema.spec.core :as spec]
            [schema.spec.collection :as coll]
            [cleebo.schemas.annotation-schemas :refer [annotation-schema]]
            [cleebo.schemas.project-schemas :refer [project-schema]]
            [cleebo.schemas.user-schemas :refer [user-schema]]))

(def project-name-schema s/Str)
(def ann-key-schema s/Str)
(def hit-token-schema
  {;; required keys
   (s/required-key :word)   s/Str
   (s/required-key :id)     s/Any
   ;; optional keys
   (s/optional-key :marked) s/Bool
   (s/optional-key :anns)   {project-name-schema {ann-key-schema annotation-schema}}
   ;; any other additional keys
   s/Keyword                s/Any})

(def hit-meta-schema
  {;; optional keys
   (s/optional-key :marked) s/Bool
   (s/optional-key :has-marked) s/Bool
   ;; any other additional keys
   s/Keyword                s/Any})

(def results-by-id-schema
  "Internal representation of results. A map from ids to hit-maps"
  {s/Int {:hit  [hit-token-schema]
          :id   s/Int
          :meta hit-meta-schema}})

(def results-schema
  "Current results being displayed are represented as an ordered list
  of hits ids. Each `id` map to an entry in the :results-by-id map"
  [s/Int])

(def query-opts-schema
  {:corpus s/Str
   :context s/Int
   :size s/Int
   :criterion s/Str
   :attribute s/Str})

(def query-results-schema
  {:query-size s/Int
   :query-str  s/Str
   :from       s/Int
   :to         s/Int
   :status {:status         (s/enum :ok :error)
            :status-content s/Str}})

(def notification-schema
  {(s/required-key :id) s/Any
   (s/required-key :data) {(s/required-key :message) s/Any
                           (s/optional-key :by)      s/Any
                           (s/optional-key :status)  (s/enum :ok :error :info)
                           (s/optional-key :date)    s/Any}})

(def settings-schema
  {:notifications {:delay s/Int}
   :snippets {:snippet-delta s/Int
              :snippet-size s/Int}})

(def ws-history-schema
  [{:received s/Int
    :type s/Keyword
    :data {s/Any s/Any}}])

(def query-history-schema
  [{:query-str s/Str :received s/Int}])

(def history-schema
  {:ws ws-history-schema
   :query query-history-schema})

(def public-user-schema
  (-> user-schema
      (dissoc :projects)
      (assoc :active s/Bool)))

(def app-error-schema
  {:error s/Str
   :message s/Str
   (s/optional-key s/Any) s/Any})

(def db-schema
  {:settings settings-schema
   :history history-schema
   :session {(s/optional-key :session-error) app-error-schema
             :query-opts query-opts-schema
             :query-results query-results-schema
             :results-by-id (s/conditional empty? {} :else results-by-id-schema)
             :results (s/conditional empty? [] :else results-schema)
             ;; user-related
             (s/optional-key :user-info) user-schema
             (s/optional-key :users) [public-user-schema]
             (s/optional-key :corpora) [s/Str]
             (s/optional-key :active-project) {:name s/Str :filtered-users #{s/Str}}
             ;; component-related
             :active-panel s/Keyword
             :notifications {s/Any notification-schema}
             (s/optional-key :modals)     {s/Keyword s/Any}
             (s/optional-key :throbbing?) {s/Any s/Bool}
             (s/optional-key :has-error?) {s/Keyword s/Any}}})

;;; reworking
(def query-opts-schema
  {:query-opts {:context s/Int :from s/Int :page-size s/Int}
   :sort-match-opts {:attribute s/Str :facet s/Str}
   :sort-context-opts {:attribute s/Str :facet s/Str}
   :filter-opts {:attribute s/Str :value s/Str}
   :snippet-opts {:snippet-size s/Int}})

(def query-meta-schema
  {:page {:from s/Int :to s/Int}
   :size s/Int
   :query-str s/Str
   :status {:status (s/enum :ok :error) :content s/Str}})

(def project-session-schema
  {:query {:results-data query-meta-schema ;info about last query
           :results (s/conditional empty? [] :else results-schema) ;current hits ids
           :results-by-id (s/conditional empty? {} :else results-by-id-schema)} ;current hits by id
   :settings {:opts query-opts-schema}  ;project-specific settings; might overwrite global-settings
   :filtered-users #{s/Str}})           ;filter out annotations by other users

(def new-db-schema
  {:settings settings-schema            ;global settings (may be overwritten by project-settings)
   :corpora [s/Any]                     ;see query-backends/Corpus
   :users [{:name s/Str                 ;users associated with client
            :user public-user-schema}]
   :history history-schema              ;keeps track of events
   :projects [{:name s/Str              
               :project project-schema
               :session project-session-schema}]
   :session {:active-project s/Str
             :active-panel s/Keyword
             :notifications {s/Any notification-schema}
             (s/optional-key :modals)     {s/Keyword s/Any}
             (s/optional-key :throbbing?) {s/Any s/Bool}
             (s/optional-key :component-error?) {s/Keyword s/Any}
             (s/optional-key :session-error) app-error-schema}})
