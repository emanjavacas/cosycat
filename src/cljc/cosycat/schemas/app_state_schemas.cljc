(ns cosycat.schemas.app-state-schemas
  (:require [schema.core :as s]
            [schema.spec.core :as spec]
            [schema.spec.collection :as coll]
            [cosycat.schemas.project-schemas :refer [project-schema]]
            [cosycat.schemas.user-schemas :refer [user-schema settings-schema query-id-schema]]))

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
  {;; ----------------
   ;; dynamic app data
   ;; ----------------
   
   ;; mutable component-related data
   :session session-schema
   
   ;; current session-settings (ev. overridden by project-settings)
   :settings settings-schema
   
   ;; ---------------
   ;; static app data
   ;; ---------------
   
   ;; client user data
   ;; - not strictly identical with user db data (db user settings are merged with projects)
   :me user-schema
   
   ;; public users info
   :users [{:username s/Str :user public-user-schema}]
   
   ;; corpus config info (from config.edn) plus
   ;; corpus-backend specific info (from cosycat.query-backends.protocol/corpus-info)
   :corpora [s/Any]
   
   ;; tagsets included in the dataset (from config.edn)
   :tagsets [s/Any]
   
   ;; project related data
   :projects {s/Any project-schema}})
