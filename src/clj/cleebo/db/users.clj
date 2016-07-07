(ns cleebo.db.users
  (:require [monger.collection :as mc]
            [monger.operators :refer :all]
            [buddy.hashers :as hashers]
            [taoensso.timbre :as timbre]
            [schema.core :as s]
            [cleebo.schemas.user-schemas :refer [user-schema]]
            [cleebo.components.db :refer [new-db colls]]
            [cleebo.avatar :refer [user-avatar]]))

(defn normalize-user
  "transforms db user doc into public user (no private info)"
  [user & ks] 
  (-> (apply dissoc user :password :_id ks)
      (update-in [:roles] (partial apply hash-set))))

(defn create-new-user
  "transforms client user payload into a db user doc"
  [{:keys [username password roles] :as user-payload}]
  (let [now (System/currentTimeMillis)]
    (-> user-payload
        (assoc :password (hashers/encrypt password {:alg :bcrypt+blake2b-512}))
        (assoc :roles roles :created now :last-active now :avatar (user-avatar username)))))

(defn new-user
  "insert user into "
  [{db-conn :db :as db} {:keys [username password firstname lastname email] :as user}
   & {:keys [roles] :or {roles ["user"]}}] ;app-roles
  (if (not (mc/find-one-as-map db-conn (:users colls) {:username username}))
    (-> (mc/insert-and-return db-conn (:users colls) (create-new-user user)) normalize-user)))

(defn ex-user-exists
  "throws proper exception in case user exists"
  [{old-username :username old-email :email} {new-username :username new-email :email}]
  (let [ex (fn [reason] (ex-info "User already exist" {:reason reason}))]
    (cond
      (and (= new-username old-username) (= new-email old-email) (ex [:username :email]))
      (= new-username old-username) (ex :username)
      (= new-email old-email) (ex :email))))

(defn is-user?
  "user check. returns nil or ex-info (in case a exception has to be thrown)"
  [{db-conn :db :as db} {:keys [username password firstname lastname email]}]
  (-> (mc/find-one-as-map
       db-conn (:users colls)
       {$or [{:username username} {:email email}]})
      ex-user-exists))

(s/defn lookup-user
  "user authentication logic"
  [{db-conn :db :as db} {:keys [username password]}] :- (s/maybe user-schema)
  (if-let [user (mc/find-one-as-map
                 db-conn (:users colls)
                 {$or [{:username username} {:email username}]})]
    (if (hashers/check password (:password user))
      (-> user normalize-user))))

(defn remove-user
  "remove user from database"           ;TODO: remove all info related to user
  [{db-conn :db :as db} username]
  (if (is-user? db {:username username})
    (mc/remove db-conn (:users colls) {:username username})))

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

