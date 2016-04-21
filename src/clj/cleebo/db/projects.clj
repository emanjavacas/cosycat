(ns cleebo.db.projects
  (:require [monger.collection :as mc]
            [monger.operators :refer :all]
            [schema.core :as s]
            [cleebo.schemas.project-schemas :refer [project-schema update-schema]]
            [cleebo.app-utils :refer [default-project-name]]
            [cleebo.roles :refer [check-project-role]]
            [cleebo.db.users :refer [is-user?]]
            [cleebo.components.db :refer [new-db]]
            [taoensso.timbre :as timbre]))

(defn is-project? [{db-conn :db} project-name]
  (boolean (mc/find-one-as-map db-conn "projects" {:name project-name})))

(defn find-project [{db-conn :db} project-name]
  (mc/find-one-as-map db-conn "projects" {:name project-name}))

(defn is-authorized?
  ([{users :users :as project} username action]
   (if-let [{role :role} (first (filter #(= username (:username %)) users))]
     (check-project-role action role)))
  ([{db-conn :db :as db} project-name username action]
   (let [project (find-project db project-name)]
     (is-authorized? project username action))))

(defn process-users [creator users]
  (map #(select-keys % [:username :role])
       (cons {:username creator :role "creator"} ;add creator to users
             (map #(assoc % :role "user") users)) ;assign default role to users
       ))

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
        {:name project-name
         :creator creator
         :description description
         :created (System/currentTimeMillis)
         :users (process-users creator users)
         :updates []})
       (dissoc :_id))))

(defn user-projects
  [{db-conn :db} username]
  (->> (mc/find-maps
        db-conn "projects"
        {$or [{:creator username} {"users.username" username}]})
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
