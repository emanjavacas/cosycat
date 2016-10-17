(ns cosycat.db.users
  (:require [monger.collection :as mc]
            [monger.operators :refer :all]
            [buddy.hashers :as hashers]
            [taoensso.timbre :as timbre]
            [schema.core :as s]
            [cosycat.app-utils :refer [not-implemented]]
            [cosycat.db.utils :refer [->set-update-map normalize-user is-user?]]
            [cosycat.schemas.user-schemas :refer [user-schema]]
            [cosycat.components.db :refer [new-db colls]]
            [cosycat.avatar :refer [user-avatar]]))

;;; Exceptions
(defn- ex-user-exists
  "returns a exception to be thrown in case user exists"
  ([data] (ex-info "User already exist" {:code :user-exists
                                         :data data
                                         :message "User already exist"}))
  ([{old-name :username old-email :email} {new-name :username new-email :email}]
   (cond
     (and (= new-name old-name) (= new-email old-email)) (ex-user-exists [:username :email])
     (= new-name old-name) (ex-user-exists :username)
     (= new-email old-email) (ex-user-exists :email))))

;;; Checkers
(defn- check-user-exists
  "user check. returns nil or ex-info (in case a exception has to be thrown)"
  [{db-conn :db :as db} {:keys [username email] :as new-user}]
  (if-let [old-user (is-user? db new-user)]
    (throw (ex-user-exists old-user new-user))))

;;; Updates
(defn- create-new-user
  "transforms client user payload into a db user doc"
  [{:keys [username password email roles] :as user-payload :or {roles ["user"]}}]
  (let [now (System/currentTimeMillis)]
    (-> user-payload
        (dissoc :csrf)
        (assoc :password (hashers/encrypt password {:alg :bcrypt+blake2b-512}))
        (assoc :roles roles :created now :last-active now :avatar (user-avatar username email)))))

(defn new-user
  "insert user into db"
  [{db-conn :db :as db} user]
  (check-user-exists db user)
  (-> (mc/insert-and-return db-conn (:users colls) (create-new-user user))
      (normalize-user :settings)))

(defn remove-user
  "remove user from database"           ;TODO: remove all info related to user
  [{db-conn :db :as db} username]
  (mc/remove db-conn (:users colls) {:username username}))

(defn- update-user
  "perform an update based on update-map"
  [{db-conn :db :as db} username update-map]
  (mc/find-and-modify
   db-conn (:users colls)
   {:username username}
   {$set update-map}
   {:return-new true}))

(defn update-user-info
  [db username update-map]
  (check-user-exists db update-map)
  (-> (update-user db username update-map) normalize-user))

(defn user-logout
  "function called on user logout event"
  [{db-conn :db :as db} username]
  (update-user db username {:last-active (System/currentTimeMillis)}))

;;; Fetchers
(defn lookup-user
  "user authentication logic"
  [{db-conn :db :as db} {:keys [username email password] :as user}]
  (if-let [db-user (mc/find-one-as-map
                    db-conn (:users colls)
                    {$or [{:username username} {:email email}]})]
    (when (hashers/check password (:password db-user))
      (-> db-user (normalize-user :settings :projects)))))

(defn- user-info
  "retrieve user client info"
  [{db-conn :db :as db} username]
  (-> (mc/find-one-as-map
       db-conn (:users colls)
       {:username username})
      normalize-user))

(defn user-login-info
  [db username] (user-info db username))

(defn user-public-info
  "retrieve user info as public user"
  [{db-conn :db :as db} username]
  (-> (mc/find-one-as-map
       db-conn (:users colls)
       {:username username})
      (normalize-user :settings :projects)))

(defn get-related-users [{db-conn :db :as db} username]
  (let [projects (->> (mc/find-maps db-conn (:projects colls) {"users.username" username}))]
    (reduce (fn [acc {:keys [users creator]}]            
              (into acc (conj (map :username users) creator)))
            []
            projects)))

(defn users-public-info
  "retrieves all users that interact with user, processed as public users (no private info)"
  [{db-conn :db :as db} username] ;todo, retrieve only users with which users has interactions
  (let [usernames (get-related-users db username)]
    (->> (mc/find-maps db-conn (:users colls) {:username {$in usernames}})
         (map #(normalize-user % :projects :settings)))))

;;; user settings
(defn user-settings
  [{db-conn :db :as db} username]
  (-> (user-info db username)
      (get :settings {})))

(defn update-user-settings
  [{db-conn :db :as db} username update-map]
  ;; do a check on settings
  (-> (update-user db username {:settings update-map})
      (get :settings {})))

(defn user-project-settings
  [db username project-name]
  (-> (user-info db username)
      (get-in [:projects project-name :settings] {})))

(defn update-user-project-settings
  [{db-conn :db} username project-name update-map]
  (-> (mc/find-and-modify
        db-conn (:users colls)
        {:username username}
        {$set (->set-update-map (format "projects.%s.settings" project-name) update-map)}
        {:return-new true})
      (get-in [:projects (keyword project-name) :settings] {})))

;;; user-dependent history
(defn register-user-project-event
  [{db-conn :db :as db} username project event]
  (let [now (System/currentTimeMillis)]
    (mc/find-and-modify
     db-conn (:users colls)
     {:username username}
     {$push {(format "projects.%s.events" project) (assoc event :timestamp now)}})))

(defn user-project-events
  [{db-conn :db :as db} username project & {:keys [from max-events] :or {max-events 10}}]
  (let [from (or from (System/currentTimeMillis))
        project-str (format "$projects.%s.events" project)]
    (mc/aggregate
     db-conn (:users colls)
     [{$match {:username username}}
      {$unwind project-str}
      {$project {:data (str project-str ".data")
                 :timestamp (str project-str ".timestamp")
                 :type (str project-str ".type")
                 :_id 0}}
      {$match {:timestamp {$lte from}}}
      {$sort {:timestamp -1}}
      {$limit max-events}])))
