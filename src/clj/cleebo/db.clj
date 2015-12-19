(ns cleebo.db
  (:require [monger.core :as mg]
            [monger.collection :as mc]
            [monger.operators :refer :all]
            [com.stuartsierra.component :as component]
            [environ.core :refer [env]]))

(defrecord DB [db conn url]
  component/Lifecycle 
  (start [component]
    (if (and conn db)
      component
      (let [{:keys [conn db]} (mg/connect-via-uri url)]
        (assoc component :db db :conn conn))))
  (stop [component]
    (if-not conn
      component
      (let [conn (:conn component)]
        (mg/disconnect conn)
        (assoc component :db nil :conn nil)))))

(defn new-db [{:keys [url]}]
  (map->DB {:url url}))

(defn add-user [db {:keys [username password]}]
  nil)
