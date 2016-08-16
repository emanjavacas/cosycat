(ns cleebo.db.projects
  (:require [monger.collection :as mc]
            [monger.operators :refer :all]
            [schema.core :as s]
            [cleebo.vcs :as vcs]
            [cleebo.app-utils :refer [server-project-name]]
            [cleebo.schemas.project-schemas
             :refer [project-schema update-schema project-user-schema]]
            [cleebo.roles :refer [check-project-role]]
            [cleebo.db.users :refer [is-user?]]
            [cleebo.components.db :refer [new-db colls]]
            [taoensso.timbre :as timbre]))

(defn ex-user [username]
  (ex-info "User doesn't exist" {:message :missing-user :data {:username username}}))

(defn ex-project [project-name]
  (ex-info "Project already exist" {:message :project-exists :data {:project project-name}}))

(defn ex-user-project [username project-name]
  (ex-info "User is not in project" {:message :user-not-in-project
                                     :data {:username username :project project-name}}))

(defn ex-rights [username action role]
  (ex-info "User doesn't have sufficient rights"
           {:message :not-authorized
            :data {:username username :action action :role role}}))

(defn normalize-project [project]
  (dissoc project :_id))

(defn find-project-by-name [{db-conn :db} project-name]
  (mc/find-one-as-map db-conn (:projects colls) {:name project-name}))

(defn get-user-role
  ([project username]
   (some (fn [{user :username role :role}] (when (= user username) role)) (:users project)))
  ([{db-conn :db :as db} project-name username]
   (let [project (find-project-by-name db project-name)]
     (get-user-role project username))))

(defn is-project? [db project-name]
  (boolean (find-project-by-name db project-name)))

(defn is-authorized?
  ([{users :users :as project} username action]
  (if-let [{role :role} (first (filter #(= username (:username %)) users))]
    (check-project-role action role)))
  ([{db-conn :db :as db} project-name username action]
   (let [project (find-project-by-name db project-name)]
     (is-authorized? project username action))))

(defn check-new-project [db project-name & [users]]
  (let [missing-username (some #(when (not (is-user? db {:username %})) %) (map :username users))]
    (cond
      missing-username (throw (ex-user missing-username))
      (is-project? db project-name) (throw (ex-project project-name)))))

(defn new-project
  "creates a new project"
  [{db-conn :db :as db} creator project-name description & [users]]
  (check-new-project db project-name users)
  (-> (mc/insert-and-return
       db-conn (:projects colls)
       {:name project-name
        :description description
        :created (System/currentTimeMillis)
        :users (map #(select-keys % [:username :role]) (conj users {:username creator :role "creator"}))})
      normalize-project))

(defn check-user-in-project [db username project-name]
  (if-not (is-authorized? db project-name username :read)
    (throw (ex-user-project username project-name))))

(defn update-project
  "adds a project update to `project.updates`"
  [{db-conn :db :as db} username project-name update-payload]
  (check-user-in-project db username project-name)
  (-> (mc/find-and-modify
       db-conn (:projects colls)
       {:name project-name}
       {$push {:updates update-payload}}
       {:return-new true})
      normalize-project))

(defn add-user
  "adds user to project"
  [{db-conn :db :as db} username project-name user]
  (check-user-in-project db username project-name)
  (mc/find-and-modify db-conn (:projects colls) {"name" project-name} {$push {"users" user}} {:return-new true}))

(defn remove-user
  "removes user from project"
  [{db-conn :db :as db} username project-name]
  (check-user-in-project db username project-name)
  (mc/find-and-modify
   db-conn (:projects colls)
   {"name" project-name}
   {$pull {"users" {"username" username}}}
   {:return-new true}))

(defn get-project
  "retrieves project by name"
  [{db-conn :db :as db} username project-name]
  (check-user-in-project db username project-name)
  (-> (mc/find-one-as-map db-conn (:projects colls) {"name" project-name})
      normalize-project))

(defn get-projects
  "retrieves data of projects in which `username` is involved"
  [{db-conn :db} username]
  (->> (mc/find-maps db-conn (:projects colls) {"users.username" username})
       (mapv normalize-project)))

(defn erase-project
  "drops the project annotations and removes the project info from users"
  [{db-conn :db :as db} project-name users]
  (vcs/drop db-conn (server-project-name project-name))
  (mc/remove db-conn (:projects colls) {:name project-name})
  (mc/update db-conn (:users colls) {:name users} {$pull {:projects {:name project-name}}})
  nil)

(defn delete-payload [username]
  {:type "delete-project-agree"
   :username username
   :timestamp (System/currentTimeMillis)})

(defn remove-project
  "drops the collections and removes project from users info (as per `erase-project`) if all users
  agree to delete the project, otherwise adds user to agreeing"
  [{db-conn :db :as db} username project-name]
  (let [project (find-project-by-name db project-name)
        role (get-user-role project username)]
    (when-not (check-project-role :delete role)
      (throw (ex-rights username :delete role)))
    (let [{:keys [updates users] :as project} (update-project db username project-name (delete-payload username))
          users (->> users (filter #(not= (:role %) "guest")) (map :username))
          agrees (into (hash-set) (->> updates (filter #(= "delete-project-agree" (:type %))) (map :username)))]
      (if (every? agrees users)
        (erase-project db project-name (:users project))
        project))))
