(ns cosycat.schemas.app-state-schemas
  (:require [schema.core :as s]
            [schema.spec.core :as spec]
            [schema.spec.collection :as coll]
            [cosycat.schemas.project-schemas :refer [project-schema]]
            [cosycat.schemas.user-schemas :refer [user-schema settings-schema]]))

;;; history
(def event-schema
  [{:received s/Int
    :type s/Keyword
    :data {s/Any s/Any}}])

(def history-schema
  {:app-events event-schema       ;general, non-project-dependent event (eg. signup)
   :project-events event-schema   ;project-dependent event (eg. ann update)
   :user-events event-schema})    ;client event - might be project dependent (eg. query, settings)

;;; users
(def public-user-schema
  (-> user-schema (assoc :active s/Bool) (dissoc :projects)))

;;; session (component dependent data)
(def notification-schema
  {(s/required-key :id) s/Any
   (s/required-key :data) {(s/required-key :message) s/Any
                           (s/optional-key :by)      s/Any
                           (s/optional-key :status)  (s/enum :ok :error :info)
                           (s/optional-key :meta)    s/Any
                           (s/optional-key :date)    s/Any}})

(def session-schema
  {:init s/Bool
   :active-panel s/Keyword
   :active-project s/Any
   (s/optional-key :notifications) {s/Any notification-schema}
   (s/optional-key :modals)     {s/Keyword s/Any}
   (s/optional-key :session-error) (s/maybe {:message s/Str (s/optional-key :code) s/Str})
   (s/optional-key :throbbing?) {s/Any s/Bool}
   (s/optional-key :component-error) {s/Keyword s/Any}})

;;; full db-schema
(def db-schema
  {;; dynamic app data
   :session session-schema              ;mutable component-related data
   :settings settings-schema            ;mutable global session-settings (in case outside project)
   :history history-schema              ;keeps track of events(could go into session/user?)
   ;; static app data (might of course change, but less so)
   :me user-schema                     ;client user
   :users [{:username s/Str :user public-user-schema}]
   :corpora [s/Any]                     ;see query-backends/Corpus
   :projects {s/Any project-schema}})
