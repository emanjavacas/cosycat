(ns cleebo.db.projects
  (:require [monger.collection :as mc]
            [monger.operators :refer :all]
            [taoensso.timbre :as timbre]
            [cleebo.db.users :refer [is-user?]]
            [cleebo.components.db :refer [new-db]]))

(defn is-project? [{db-conn :db} project-name]
  (boolean (mc/find-one-as-map db-conn "projects" {:name project-name})))

(defn new-project
  [{db-conn :db :as db} creator name description & users]
  (assert (every? (partial is-user? db) users))
  (assert (not (is-project? db name)))
  (mc/insert-and-return
   db-conn "projects"
   {:creator creator
    :name name
    :description description
    :users (or users [])
    :updates []}))

(defn user-projects
  [{db-conn :db} username]
  (mc/find-maps
   db-conn "projects"
   {$or [{:creator username} {:users username}]}))

(defn update-project
  [{db-conn :db} username project-name update-payload]
  (let [{creator :creator users :users updates :updates}
        (mc/find-and-modify
         db-conn "projects"
         {:name project-name})]))
