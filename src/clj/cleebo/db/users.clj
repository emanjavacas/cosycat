(ns cleebo.db.users
  (:require [monger.collection :as mc]
            [monger.operators :refer :all]
            [buddy.hashers :as hashers]
            [taoensso.timbre :as timbre]
            [cleebo.components.db :refer [new-db]]
            [cleebo.db.roles :refer [app-roles]]))

(defn ->keyword
  "hack namespaced keywords"
  [s]
  (keyword (str "cleebo.db.roles/" s)))

(def coll "users")

(defn new-user [{db-conn :db :as db} {:keys [username password]} &
                {:keys [roles] :or {roles [:user]}}]
  (let [roles (filter identity (map app-roles roles))]
    (if (not (mc/find-one-as-map db-conn coll {:username username}))
      (let [user {:username username
                  :password (hashers/encrypt password {:alg :bcrypt+blake2b-512})
                  :roles roles}]
        (-> (mc/insert-and-return db-conn coll user)
            (dissoc :password)
            (dissoc :_id)
            (update :roles #(into #{} %)))))))

(defn is-user? [{db-conn :db :as db} {:keys [username password]}]
  (boolean (mc/find-one-as-map db-conn coll {:username username})))

(defn lookup-user [{db-conn :db :as db} username password]
  (if-let [user (mc/find-one-as-map db-conn coll {:username username})]
    (if (hashers/check password (:password user))
      (-> user
          (update :roles #(into #{} (map ->keyword %)))
          (dissoc :password)
          (dissoc :_id)))))

(defn remove-user [{db-conn :db :as db} username]
  (if (is-user? db {:username username})
    (mc/remove db-conn coll {:username username})))

(defn user-session [{db-conn :db} username]
  (mc/find-one-as-map
   db-conn coll
   {:username username}
   {:password false :_id false}))

;; (def db (.start (new-db {:url "mongodb://127.0.0.1:27017/cleeboTest"})))

