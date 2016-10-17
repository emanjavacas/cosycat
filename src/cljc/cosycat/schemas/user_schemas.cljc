(ns cosycat.schemas.user-schemas
  (:require [schema.core :as s]
            [cosycat.schemas.event-schemas :refer [event-schema]]))

(def avatar-schema
  {:href s/Str
   :dominant-color s/Str})

;;; query opts (used both in [:me :settings] and [:projects [{:settings}])
(def query-opts-schema
  {:corpus s/Str
   :query-opts {:context s/Int :from s/Int :page-size s/Int} ;from is kept updated
   :sort-opts [{:position s/Str :attribute s/Str :facet s/Str}]
   :filter-opts [{:attribute s/Str :value s/Str}]            ;multiple filters
   :snippet-opts {:snippet-size s/Int :snippet-delta s/Int}})

(def settings-schema
  {:notifications {:delay s/Int} ;overridable by project-setts
   :query query-opts-schema
   (s/optional-key :tagsets) [s/Any]})

(def user-project-schema   ;server-only (get merged with project in the client)
  {:settings settings-schema            ;project-specific settings
   :events [event-schema]})             ;user-specific project events (queries, etc.)

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
