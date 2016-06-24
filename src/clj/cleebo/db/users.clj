(ns cleebo.db.users
  (:require [monger.collection :as mc]
            [monger.operators :refer :all]
            [buddy.hashers :as hashers]
            [taoensso.timbre :as timbre]
            [schema.core :as s]
            [cleebo.schemas.user-schemas :refer [user-schema]]
            [cleebo.db.projects]
            [cleebo.components.db :refer [new-db colls]]
            [cleebo.avatar :refer [user-avatar]]))

(defn postprocess-user [user & ks] 
  (-> (apply dissoc user :password :_id ks)
      (update-in [:roles] (partial apply hash-set))))

(defn new-user
  [{db-conn :db :as db} {:keys [username password firstname lastname email] :as user}
   & {:keys [roles] :or {roles ["user"]}}] ;app-roles
  (if (not (mc/find-one-as-map db-conn (:users colls) {:username username}))
    (let [now (System/currentTimeMillis)
          user (assoc user
                :password (hashers/encrypt password {:alg :bcrypt+blake2b-512})
                :roles roles :created now :last-active now :avatar (user-avatar username))]
      (-> (mc/insert-and-return db-conn (:users colls) user)
          postprocess-user))))

(defn is-user?
  [{db-conn :db :as db} {:keys [username password firstname lastname email]}]
  (boolean
   (mc/find-one-as-map
    db-conn (:users colls)
    {$or [{:username username}
          {$and [{:firstname firstname :lastname lastname}]}
          {:email email}]})))

(s/defn lookup-user
  [{db-conn :db :as db} {:keys [username password]}] :- (s/maybe user-schema)
  (if-let [user (mc/find-one-as-map
                 db-conn (:users colls)
                 {$or [{:username username} {:email username}]})]
    (if (hashers/check password (:password user))
      (-> user postprocess-user))))

(defn remove-user [{db-conn :db :as db} username]
  (if (is-user? db {:username username})
    (mc/remove db-conn (:users colls) {:username username})))

(defn user-logout [{db-conn :db :as db} username]
  (if (is-user? db {:username username})
    (mc/find-and-modify
     db-conn (:users colls)
     {:username username}
     {$set {:last-active (System/currentTimeMillis)}}
     {})))

(defn user-info
  [{db-conn :db :as db} username]
  (-> (mc/find-one-as-map
       db-conn (:users colls)
       {:username username}
       {:password false :_id false})
      postprocess-user))

(defn update-user-info
  [{db-conn :db :as db} username update-map]
  (mc/find-and-modify
   db-conn (:users colls)
   {:username username}
   {$set update-map}
   {}))

(defn users-info [{db-conn :db}]
  (->> (mc/find-maps db-conn (:users colls) {})
       (map #(postprocess-user % :projects))))

