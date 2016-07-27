(ns cleebo.db.users
  (:require [monger.collection :as mc]
            [monger.operators :refer :all]
            [buddy.hashers :as hashers]
            [taoensso.timbre :as timbre]
            [schema.core :as s]
            [cleebo.schemas.user-schemas :refer [user-schema]]
            [cleebo.components.db :refer [new-db colls]]
            [cleebo.avatar :refer [user-avatar]]))

(defn ex-user-exists
  "returns a exception to be thrown in case user exists"
  ([data] (ex-info "User already exist" {:message :user-exists :data data}))
  ([{old-name :username old-email :email} {new-name :username new-email :email}]
   (cond
     (and (= new-name old-name) (= new-email old-email)) (ex-user-exists [:username :email])
     (= new-name old-name) (ex-user-exists :username)
     (= new-email old-email) (ex-user-exists :email))))

(defn normalize-user
  "transforms db user doc into public user (no private info)"
  [user & ks] 
  (-> (apply dissoc user :password :_id ks)
      (update-in [:roles] (partial apply hash-set))))

(defn is-user?
  [{db-conn :db :as db} {:keys [username firstname lastname email]}]
  (some-> (mc/find-one-as-map db-conn (:users colls) {$or [{:username username} {:email email}]})
          normalize-user))

(defn check-user-exists
  "user check. returns nil or ex-info (in case a exception has to be thrown)"
  [{db-conn :db :as db} {:keys [username email] :as new-user}]
  (if-let [old-user (is-user? db new-user)]
    (throw (ex-user-exists old-user new-user))))

(defn create-new-user
  "transforms client user payload into a db user doc"
  [{:keys [username password roles] :as user-payload :or {roles ["user"]}}]
  (let [now (System/currentTimeMillis)]
    (-> user-payload
        (dissoc :csrf)
        (assoc :password (hashers/encrypt password {:alg :bcrypt+blake2b-512}))
        (assoc :roles roles :created now :last-active now :avatar (user-avatar username)))))

(defn new-user
  "insert user into db"
  [{db-conn :db :as db} user]
  (check-user-exists db user)
  (-> (mc/insert-and-return db-conn (:users colls) (create-new-user user)) normalize-user))

(s/defn lookup-user
  "user authentication logic"
  [{db-conn :db :as db} {:keys [username email password] :as user}] :- (s/maybe user-schema)
  (if-let [db-user (mc/find-one-as-map db-conn (:users colls) {$or [{:username username} {:email email}]})]
    (if (hashers/check password (:password db-user))
      (-> db-user normalize-user))))

(defn remove-user
  "remove user from database"           ;TODO: remove all info related to user
  [{db-conn :db :as db} username]
  (mc/remove db-conn (:users colls) {:username username}))

(defn user-logout
  "function called on user logout event"
  [{db-conn :db :as db} username]
  (if (is-user? db {:username username})
    (mc/find-and-modify
     db-conn (:users colls)
     {:username username}
     {$set {:last-active (System/currentTimeMillis)}}
     {})))

(defn user-info
  "retrieve client info as public user (no private info)"
  [{db-conn :db :as db} username]
  (-> (mc/find-one-as-map
       db-conn (:users colls)
       {:username username})
      normalize-user))

(defn update-user-info
  "perform an update based on update-map"
  [{db-conn :db :as db} username update-map]
  (mc/find-and-modify
   db-conn (:users colls)
   {:username username}
   {$set update-map}
   {}))

(defn users-info
  "retrieves all users processed as public users (no private info)"
  [{db-conn :db}] ;todo, retrieve only users with which users has interactions
  (->> (mc/find-maps db-conn (:users colls) {}) (map #(normalize-user % :projects))))

