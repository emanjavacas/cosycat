(ns cosycat.db.projects
  (:require [monger.collection :as mc]
            [monger.operators :refer :all]
            [schema.core :as s]
            [cosycat.vcs :as vcs]
            [cosycat.app-utils :refer [server-project-name pending-users]]
            [cosycat.schemas.project-schemas
             :refer [project-schema project-user-schema issue-schema]]
            [cosycat.roles :refer [check-project-role]]
            [cosycat.db.utils :refer [is-user? normalize-project]]
            [cosycat.components.db :refer [new-db colls]]
            [taoensso.timbre :as timbre]))

(defn ex-user [username]
  (ex-info "User doesn't exist"
           {:message "User doesn't exist" :code :missing-user
            :data {:username username}}))

(defn ex-project [project-name]
  (ex-info "Project already exist"
           {:code :project-exists :message "Project already exist"
            :data {:project project-name}}))

(defn ex-non-existing-project [project-name]
  (ex-info "Project doesn't exist"
           {:code :missing-project
            :message "Project doesn't exist"
            :data {:project project-name}}))

(defn ex-user-project [username project-name]
  (ex-info "User is not in project"
           {:code :user-not-in-project
            :message "User is not in project"
            :data {:username username :project project-name}}))

(defn ex-rights [username action role]
  (ex-info (str username " doesn't have sufficient rights")
           {:code :not-authorized
            :message (str username " doesn't have sufficient rights")
            :data {:username username :action action :role role}}))

(defn ex-last-user [project-name]
  (ex-info (str "Attempt to remove last user in project: " project-name)
           {:code :last-user-in-project
            :message (str "Attempt to remove last user in project: " project-name)
            :data {:project-name project-name}}))

(defn ex-pending [project-name username]
  (ex-info (str username "'s role can't be updated because of pending issues")
           {:code :pending-issues
            :message (str username "'s role can't be updated because of pending issues")
            :data {:project-name project-name :username username}}))

(defn has-pending-issue? [{:keys [issues] :as project} username]
  (some (fn [{issue-users :users :as issue}]
          (contains? (apply hash-set issue-users) username))
        issues))

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

(defn check-user-in-project [db username project-name]
  (if-not (is-authorized? db project-name username :read)
    (throw (ex-user-project username project-name))))

(defn check-project-empty
  [db project-name & {:keys [transform-f] :or {transform-f identity}}]
  (let [project (find-project-by-name db project-name)]
    (if-let [users (seq (-> project transform-f :users))]
      :ok
      (throw (ex-last-user project-name)))))

(defn join-project-creator [creator users]
  (conj users {:username creator :role "creator"}))

(defn new-project
  "creates a new project"
  [{db-conn :db :as db} creator project-name description & [users]]
  (check-new-project db project-name users)
  (-> (mc/insert-and-return
       db-conn (:projects colls)
       {:name project-name
        :description description
        :created (System/currentTimeMillis)
        :creator creator
        :users (->> users (join-project-creator creator) (map #(select-keys % [:username :role])))})
      normalize-project))

(defn get-project
  "retrieves project by name"
  [{db-conn :db :as db} username project-name]
  (check-user-in-project db username project-name)
  (-> (mc/find-one-as-map db-conn (:projects colls) {"name" project-name})
      normalize-project))

(defn update-project
  "adds issue to `project.issues`"
  [{db-conn :db :as db} username project-name issue-payload]
  (check-user-in-project db username project-name)
  (s/validate issue-schema issue-payload)
  (-> (mc/find-and-modify
       db-conn (:projects colls)
       {:name project-name}
       {$push {:issues issue-payload}}
       {:return-new true})
      normalize-project))

(defn add-user
  "adds user to project"
  [{db-conn :db :as db} username project-name user]
  (check-user-in-project db username project-name)
  (-> (mc/find-and-modify
       db-conn (:projects colls)
       {"name" project-name}
       {$push {"users" user}}
       {:return-new true})
      normalize-project))

(defn- remove-from-users [users username]
  (remove #(= username (:username %)) users))

(defn remove-user
  "removes user from project"
  [{db-conn :db :as db} username project-name]
  (check-user-in-project db username project-name)
  (check-project-empty db project-name :transform-f #(update % :users remove-from-users username))
  (-> (mc/find-and-modify
       db-conn (:projects colls)
       {"name" project-name}
       {$pull {"users" {"username" username}}}
       {:return-new true})
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

(defn get-delete-payload [username]
  {:type "delete-project-agree"
   :status "open"
   :username username
   :users [:all]
   :timestamp (System/currentTimeMillis)})

(defn remove-project
  "drops the collections and removes project from users info (as per `erase-project`) if all users
  agree to delete the project, otherwise adds user to agreeing"
  [{db-conn :db :as db} username project-name]
  (let [project (find-project-by-name db project-name)
        role (get-user-role project username)]
    (when-not project
      (throw (ex-non-existing-project project-name)))
    (when-not (check-project-role :delete role)
      (throw (ex-rights username :delete role)))
    (let [delete-payload (get-delete-payload username)
          {:keys [users] :as project} (update-project db username project-name delete-payload)
          {:keys [pending non-app agreed-users]} (pending-users project)]
      (if (empty? pending)
        (erase-project db project-name users)
        delete-payload))))

(defn update-user-role [{db-conn :db :as db} issuer project-name username new-role]
  (let [project (find-project-by-name db project-name)
        role (get-user-role db project-name issuer)]
    (when-not role
      (throw (ex-user username)))
    (when (has-pending-issue? project username)
      (throw (ex-pending project-name username)))
    (when-not (check-project-role :write role)
      (throw (ex-rights issuer :write role)))
    (->> (mc/find-and-modify
          db-conn (:projects colls)
          {"users.username" username}
          {$set {"users.$.role" new-role}}
          {:return-new true})
         :users
         (filter #(= username (:username %)))
         first)))

