(ns cosycat.schemas.user-schemas
  (:require [schema.core :as s]
            [cosycat.schemas.queries-schemas :refer [query-opts-schema review-opts-schema]]))

;;; Avatar
(def avatar-schema
  {:href s/Str
   :dominant-color s/Str})

;;; Events
(def event-id-schema s/Any)

(def event-schema
  {:id event-id-schema
   :timestamp s/Int
   (s/optional-key :repeated) [s/Int] ;field to efficiently collapse repeated events
   :type s/Any
   :data {s/Any s/Any}})

;;; Settings
(def settings-schema
  {:notifications {:delay s/Int
                   ;; restrict notifications of ws-events
                   (s/optional-key :verbosity) {s/Keyword s/Bool}}
   :query query-opts-schema
   ;; TODO: :review review-opts-schema
   (s/optional-key :tagsets) [s/Any]})

;;; User project
(def user-project-schema   ;server-only (get merged with project in the client)
  {:settings settings-schema            ;project-specific settings
   :events [event-schema]})             ;user-specific project events (queries, etc.)

;;; User
(def user-schema
  #?(:clj {:username s/Str
           :firstname s/Str
           :lastname s/Str
           :email s/Str
           :avatar avatar-schema
           :roles #{s/Str}
           :created s/Int
           :last-active s/Int
           (s/optional-key :settings) settings-schema ;saved global-settings
           (s/optional-key :projects) {s/Str user-project-schema}}
     :cljs  {:username s/Str
             :firstname s/Str
             :lastname s/Str
             :email s/Str
             :avatar avatar-schema
             :roles #{s/Str}
             :created s/Int
             :last-active s/Int
             (s/optional-key :settings) settings-schema}))
