(ns cleebo.db.projects
  (:require [monger.collection :as mc]
            [monger.operators :refer :all]
            [schema.core :as s]
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

(defn normalize-project [project]
  (dissoc project :_id))

(defn find-project-by-name [{db-conn :db} project-name]
  (mc/find-one-as-map db-conn (:projects colls) {:name project-name}))

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

(s/defn new-project :- project-schema
  [{db-conn :db :as db} creator project-name description & [users]]
  (check-new-project db project-name users)
  (-> (mc/insert-and-return
       db-conn (:projects colls)
       {:name project-name
        :description description
        :created (System/currentTimeMillis)
        :users (map #(select-keys % [:username :role]) users)})
      normalize-project))

(defn check-user-in-project [db username project-name]
  (if-not (is-authorized? db project-name username :read)
    (throw (ex-user-project username project-name))))

(s/defn update-project
  [{db-conn :db :as db} username project-name update-payload]
  (check-user-in-project db project-name username)
  (let [{:keys [creator users updates] :as payload}
        (mc/find-and-modify
         db-conn (:projects colls)
         {:name project-name}
         {$push {:updates update-payload}}
         {:return-new true})]
    normalize-project))

(s/defn add-user
  [{db-conn :db :as db} username project-name user :- project-user-schema]
  (check-user-in-project db username project-name)
  (mc/find-and-modify db-conn (:projects colls) {"name" project-name} {$push {"users" user}}))

(s/defn remove-user
  [{db-conn :db :as db} username project-name]
  (check-user-in-project db username project-name)
  (mc/find-and-modify
   db-conn (:projects colls)
   {"name" project-name}
   {$pull {"users" {"username" username}}}))

(s/defn get-project :- project-schema
  "retrieves project by name"
  [{db-conn :db :as db} username project-name]
  (check-user-in-project db username project-name)
  (-> (mc/find-one-as-map db-conn (:projects colls) {"name" project-name})
      normalize-project))

(s/defn get-projects :- [project-schema]
  "retrieves data of projects in which `username` is involved"
  [{db-conn :db} username]
  (->> (mc/find-maps db-conn (:projects colls) {"users.username" username})
       (mapv normalize-project)))

