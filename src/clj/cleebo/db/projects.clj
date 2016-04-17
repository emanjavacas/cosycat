(ns cleebo.db.projects
  (:require [monger.collection :as mc]
            [monger.operators :refer :all]
            [taoensso.timbre :as timbre]
            [cleebo.db.users :refer [is-user?]]
            [cleebo.components.db :refer [new-db]]))

(defn is-project? [{db-conn :db} project-name]
  (boolean (mc/find-one-as-map db-conn "projects" {:name project-name})))

(defn new-project
  [{db-conn :db :as db} creator name description & [users]]
  (assert (every? #(is-user? db {:username %}) users))
  (assert (not (is-project? db name)))
  (let [payload (mc/insert-and-return
                 db-conn "projects"
                 {:creator creator
                  :name name
                  :description description
                  :created (System/currentTimeMillis)
                  :users (or users [])
                  :updates []})]
    (dissoc payload :_id)))

(defn user-projects
  [{db-conn :db} username]
  (->> (mc/find-maps
        db-conn "projects"
        {$or [{:creator username} {:users username}]})
       (map #(dissoc % :_id))))

(defn update-project
  [{db-conn :db} username project-name update-payload]
  (let [{creator :creator users :users updates :updates :as payload}
        (mc/find-and-modify
         db-conn "projects"
         {:name project-name}
         {:return-new true})]
    (dissoc payload :_id)))
