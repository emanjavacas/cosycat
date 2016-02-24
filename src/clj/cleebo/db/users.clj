(ns cleebo.db.users
  (:require [monger.collection :as mc]
            [monger.operators :refer :all]
            [buddy.hashers :as hashers]
            [taoensso.timbre :as timbre]
            [cleebo.db.roles :refer [app-roles]]))

(defn ->keyword
  "hack namespaced keywords"
  [s]
  (keyword (str "cleebo.db.roles/" s)))

(def coll "users")

(defn new-user [db {:keys [username password]} & {:keys [roles] :or {roles [:user]}}]
  (let [db-conn (:db db)
        roles (filter identity (map app-roles roles))]
    (if (not (mc/find-one-as-map db-conn coll {:username username}))
      (let [user {:username username
                  :password (hashers/encrypt password {:alg :bcrypt+blake2b-512})
                  :roles roles}]
        (-> (mc/insert-and-return db-conn coll user)
            (dissoc :password)
            (dissoc :_id)
            (update :roles #(into #{} %)))))))

(defn is-user? [db {:keys [username password]}]
  (let [db-conn (:db db)]
    (boolean (mc/find-one-as-map db-conn coll {:username username}))))

(defn lookup-user [db username password]
  (let [db-conn (:db db)]
    (if-let [user (mc/find-one-as-map db-conn coll {:username username})]
      (if (hashers/check password (:password user))
        (-> user
            (update :roles #(into #{} (map ->keyword %)))
            (dissoc :password)
            (dissoc :_id))))))

(defn remove-user [db username]
  (let [db-conn (:db db)]
    (if (is-user? db {:username username})
      (mc/remove db-conn coll {:username username}))))
