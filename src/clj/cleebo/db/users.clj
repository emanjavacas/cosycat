(ns cleebo.db.users
  (:require [monger.collection :as mc]
            [monger.operators :refer :all]
            [buddy.hashers :as hashers]
            [taoensso.timbre :as timbre]
            [schema.core :as s]
            [cleebo.schemas.app-state-schemas :refer [public-user-schema user-schema]]
            [cleebo.components.db :refer [new-db]]
            [cleebo.avatar :refer [new-avatar]]))

(defn new-user
  [{db-conn :db :as db} {:keys [username password]} &
   {:keys [roles] :or {roles ["user"]}}]
  (if (not (mc/find-one-as-map db-conn "users" {:username username}))
    (let [user {:username username
                :password (hashers/encrypt password {:alg :bcrypt+blake2b-512})
                :roles roles
                :created (System/currentTimeMillis)
                :avatar (new-avatar username)}]
      (-> (mc/insert-and-return db-conn "users" user)
          (dissoc :password :_id)))))

(defn is-user? [{db-conn :db :as db} {:keys [username password]}]
  (boolean (mc/find-one-as-map db-conn "users" {:username username})))

(s/defn lookup-user
  [{db-conn :db :as db} username password] :- (s/maybe user-schema)
  (if-let [user (mc/find-one-as-map db-conn "users" {:username username})]
    (if (hashers/check password (:password user))
      (dissoc user :password :_id))))

(defn remove-user [{db-conn :db :as db} username]
  (if (is-user? db {:username username})
    (mc/remove db-conn "users" {:username username})))

(defn user-logout [{db-conn :db :as db} username]
  (if (is-user? db {:username username})
    (mc/find-and-modify
     db-conn "users"
     {:username username}
     {$set {:last-active (System/currentTimeMillis)}}
     {})))

(defn user-info [{db-conn :db :as db} username]
  (mc/find-one-as-map
   db-conn "users"
   {:username username}
   {:password false :_id false}))

(s/defn filter-user-public [user] :- public-user-schema
  (dissoc user :password :_id :projects))

(defn users-public-info [{db-conn :db}]
  (->> (mc/find-maps
        db-conn "users"
        {})
       (map filter-user-public)))

;; (def db (.start (new-db {:url "mongodb://127.0.0.1:27017/cleeboTest"})))
