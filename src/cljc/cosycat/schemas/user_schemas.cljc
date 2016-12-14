(ns cosycat.schemas.user-schemas
  (:require [schema.core :as s]
            [cosycat.schemas.event-schemas :refer [event-schema]]))

;;; Avatar
(def avatar-schema
  {:href s/Str
   :dominant-color s/Str})

;;; Query opts
(def filter-opts-schema
  {:attribute s/Str :value s/Str})

(def sort-opts-schema
  {:position s/Str :attribute s/Str :facet s/Str})

(def query-opts-schema
  {:corpus s/Str
   :query-opts {:context s/Int :from s/Int :page-size s/Int} ;from is kept updated
   :sort-opts [sort-opts-schema]
   :filter-opts [filter-opts-schema]            ;multiple filters
   :snippet-opts {:snippet-size s/Int :snippet-delta s/Int}})

;;; Settings
(def settings-schema
  {:notifications {:delay s/Int
                   ;; restrict notifications of ws-events
                   (s/optional-key :verbosity) {s/Keyword s/Bool}}
   :query query-opts-schema
   ;; TODO: review settings?
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
