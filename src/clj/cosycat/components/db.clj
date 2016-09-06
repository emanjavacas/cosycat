(ns cosycat.components.db
  (:require [monger.core :as mg]
            [monger.collection :as mc]
            [com.stuartsierra.component :as component]
            [config.core :refer [env]]
            [taoensso.timbre :as timbre]))

(defrecord DB [db conn url]
  component/Lifecycle 
  (start [component]
    (timbre/info "starting DB on" url)
    (if (and conn db)
      component
      (let [{:keys [conn db]} (mg/connect-via-uri url)]
        (assoc component :db db :conn conn))))
  (stop [component]
    (timbre/info "Shutting down DB")
    (if-not conn
      (assoc component :db nil)
      (do (mg/disconnect conn)
          (assoc component :db nil :conn nil)))))

(defn new-db [url]
  (map->DB {:url url}))

(def colls
  {:projects "projects" :users "users"})


