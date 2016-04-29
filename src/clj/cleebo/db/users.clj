(ns cleebo.db.users
  (:require [monger.collection :as mc]
            [monger.operators :refer :all]
            [buddy.hashers :as hashers]
            [taoensso.timbre :as timbre]
            [schema.core :as s]
            [cleebo.schemas.app-state-schemas :refer [public-user-schema user-schema]]
            [cleebo.components.db :refer [new-db colls]]
            [cleebo.avatar :refer [user-avatar]]))

(s/defn ^:always-validate postprocess-user-load
  [user & ks] :- public-user-schema
  (-> (apply dissoc user :password :_id ks)
      (update-in [:roles] (partial apply hash-set))))

(defn new-user
  [{db-conn :db :as db} {:keys [username password]} &
   {:keys [roles] :or {roles ["user"]}}]
  (if (not (mc/find-one-as-map db-conn (:users colls) {:username username}))
    (let [now (System/currentTimeMillis)
          user {:username username
                :password (hashers/encrypt password {:alg :bcrypt+blake2b-512})
                :roles roles
                :created now
                :last-active now
                :avatar (user-avatar username)}]
      (-> (mc/insert-and-return db-conn (:users colls) user)
          postprocess-user-load))))

(defn is-user? [{db-conn :db :as db} {:keys [username password]}]
  (boolean (mc/find-one-as-map db-conn (:users colls) {:username username})))

(s/defn lookup-user
  [{db-conn :db :as db} username password] :- (s/maybe user-schema)
  (if-let [user (mc/find-one-as-map db-conn (:users colls) {:username username})]
    (if (hashers/check password (:password user))
      (-> user postprocess-user-load))))

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
      postprocess-user-load))

(defn update-user-info
  [{db-conn :db :as db} username update-map]
  (mc/find-and-modify
   db-conn (:users colls)
   {:username username}
   {$set update-map}
   {}))

(defn users-public-info [{db-conn :db}]
  (->> (mc/find-maps
        db-conn (:users colls)
        {})
       (map #(postprocess-user-load % :projects))))
