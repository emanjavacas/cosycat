(ns cleebo.db.projects
  (:require [monger.collection :as mc]
            [monger.operators :refer :all]
            [schema.core :as s]
            [cleebo.schemas.project-schemas :refer [project-schema update-schema]]
            [cleebo.app-utils :refer [default-project-name]]
            [taoensso.timbre :as timbre]
            [cleebo.db.users :refer [is-user?]]
            [cleebo.components.db :refer [new-db]]))

(def project-roles
  "a map from actions to required roles"
  {:delete #{"creator"}                 ;remove project
   :write  #{"creator" "project-lead"}  ;update metadata
   :update #{"creator" "project-lead" "user"}  ;push update
   :read   #{"creator" "project-lead" "user"} ;retrieve project
   })

(defn check-role [action user-role]
  (boolean (some #{user-role} (get project-roles action))))

(defn is-project? [{db-conn :db} project-name]
  (boolean (mc/find-one-as-map db-conn "projects" {:name project-name})))

(defn find-project [{db-conn :db} project-name]
  (mc/find-one-as-map db-conn "projects" {:name project-name}))

(defn is-authorized?
  ([{users :users :as project} username action]
   (if-let [{role :role} (first (filter #(= username (:username %)) users))]
     (check-role action role)))
  ([{db-conn :db :as db} project-name username action]
   (let [project (find-project db project-name)]
     (is-authorized? project username action))))

(s/defn new-project :- project-schema
  ([db creator]
   (new-project
    db
    creator
    (default-project-name creator)
    "A default project to explore the app, get started, try queries etc."))
  ([{db-conn :db :as db} creator project-name description & [users]]
   {:pre [(every? #(is-user? db {:username %}) (map :username users))
          (not (is-project? db project-name))
          (not (and (not (.startsWith project-name creator))
                    (.endsWith project-name "Playground")))]}
   (-> (mc/insert-and-return
        db-conn "projects"
        {:creator creator
         :name project-name
         :description description
         :created (System/currentTimeMillis)
         :users (or users [])
         :updates []})
       (dissoc :_id))))

(defn user-projects
  [{db-conn :db} username]
  (->> (mc/find-maps
        db-conn "projects"
        {$or [{:creator username} {:users username}]})
       (map #(dissoc % :_id))))

(s/defn update-project
  [{db-conn :db} username project-name update-payload :- update-schema]
  (let [{:keys [creator users updates] :as payload}
        (mc/find-and-modify
         db-conn "projects"
         {:name project-name}
         {$push {:updates update-payload}}
         {:return-new true})]
    (dissoc payload :_id)))
