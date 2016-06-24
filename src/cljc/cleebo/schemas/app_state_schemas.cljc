(ns cleebo.schemas.app-state-schemas
  (:require [schema.core :as s]
            [schema.spec.core :as spec]
            [schema.spec.collection :as coll]
            [cleebo.schemas.annotation-schemas :refer [annotation-schema]]
            [cleebo.schemas.project-schemas :refer [project-schema]]
            [cleebo.schemas.user-schemas :refer [user-schema settings-schema]]))

;;; hit/token schemas
(def hit-token-schema
  {;; required keys
   (s/required-key :word)   s/Str
   (s/required-key :id)     s/Any
   ;; optional keys
   (s/optional-key :marked) s/Bool      ;is token marked for annotation?
   (s/optional-key :anns)   {s/Str annotation-schema} ;ann-key to ann-map
   ;; any other additional keys
   s/Keyword                s/Any})

(def hit-id-schema s/Any)

(def hit-meta-schema
  {(s/required-key :id) hit-id          ;hit-id used to quickly identify ann updates
   (s/required-key :num) s/Int          ;index of hit in current query
   ;; optional keys 
   (s/optional-key :marked) s/Bool      ;is hit marked for annotation?
   (s/optional-key :has-marked) s/Bool  ;does hit contain marked tokens?
   ;; any other additional keys
   s/Keyword                s/Any})

;;; projects
(def results-by-id-schema
  "Internal representation of results. A map from ids to hit-maps"
  {hit-id {:hit  [hit-token-schema]
           :meta hit-meta-schema}})

(def results-schema
  "Current results being displayed are represented as an ordered list
  of hits ids. Each `id` map to an entry in the :results-by-id map"
  [hit-id])                             ;use hit-num instead?

(def results-summary-schema
  {:page {:from s/Int :to s/Int}
   :size s/Int
   :query-str s/Str
   :status {:status (s/enum :ok :error) :content s/Str}})

(def project-session-schema
  {:query {:results-summary results-summary-schema ;info about last query
           :results (s/conditional empty? [] :else results-schema) ;current hits ids
           :results-by-id (s/conditional empty? {} :else results-by-id-schema)} ;hits by id
   :filtered-users #{s/Str}             ;filter out annotations by other users
   :corpus s/Str})                      ;current corpus

;;; history
(def ws-event-history-schema
  [{:received s/Int
    :type s/Keyword
    :data {s/Any s/Any}}])

(def internal-event-history-schema
  [{:received s/Int
    :type s/Keyword
    :data {s/Any s/Any}}])

(def history-schema
  {:ws-events ws-event-history-schema
   :internal-events internal-event-history-schema})

;;; users
(def public-user-schema
  (-> user-schema (assoc :active s/Bool) (dissoc :projects)))

;;; session (highly & component dependent data)
(def notification-schema
  {(s/required-key :id) s/Any
   (s/required-key :data) {(s/required-key :message) s/Any
                           (s/optional-key :by)      s/Any
                           (s/optional-key :status)  (s/enum :ok :error :info)
                           (s/optional-key :date)    s/Any}})

(def session-error-schema
  {:error s/Str
   :message s/Str
   (s/optional-key s/Any) s/Any})

(def session-schema
  {:active-panel s/Keyword
   :settings settings-schema            ;session-settings
   :notifications {s/Any notification-schema}
   (s/optional-key :modals)     {s/Keyword s/Any}
   (s/optional-key :throbbing?) {s/Any s/Bool}
   (s/optional-key :component-error?) {s/Keyword s/Any}
   (s/optional-key :session-error) session-error-schema})

;;; full db-schema
(def db-schema
  {;; dynamic app data
   :session session-schema              ;mutable component-related data
   :history history-schema              ;keeps track of events(could go into session/user?)
   ;; static app data (might of course change, but less so)
   :me user-schema                     ;client user
   :users [{:username s/Str :user public-user-schema}]
   :corpora [s/Any]                     ;see query-backends/Corpus
   :projects
   [{:name s/Str                        ;key
     :project project-schema
     (s/optional-key :session) project-session-schema}] ;client mutable project-specific data
})
