(ns cleebo.components.db
  (:require [monger.core :as mg]
            [monger.collection :as mc]
            [com.stuartsierra.component :as component]
            [environ.core :refer [env]]
            [taoensso.timbre :as timbre]))

(defrecord DB [db conn url]
  component/Lifecycle 
  (start [component]
    (if (and conn db)
      component
      (let [{:keys [conn db]} (mg/connect-via-uri url)]
        (timbre/info "starting DB")
        (assoc component :db db :conn conn))))
  (stop [component]
    (if-not conn
      component
      (do
        (timbre/info "Shutting down DB")
        (mg/disconnect conn)
        (assoc component :db nil :conn nil)))))

(defn new-db [{:keys [url]}]
  (map->DB {:url url}))

(def colls
  {:anns "anns"
   :cpos-anns "cpos_anns"
   :projects "projects"
   :users "users"})

(defn clear-dbs
  [{db :db} & {:keys [collections] :or {colls (keys collections)}}]
  (doseq [k-coll collections
          :let [v-coll (get colls k-coll)]]
    (timbre/info "Clearing collection:" v-coll)
    (mc/drop db v-coll)))
