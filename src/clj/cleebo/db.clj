(ns cleebo.db
  (:require [monger.core :as mg]
            [monger.collection :as mc]
            [monger.operators :refer :all]
            [buddy.hashers :as hashers]
            [com.stuartsierra.component :as component]
            [environ.core :refer [env]]
            [taoensso.timbre :as timbre]))

(defrecord DB [db conn url]
  component/Lifecycle 
  (start [component]
    (if (and conn db)
      component
      (let [{:keys [conn db]} (mg/connect-via-uri url)]
        (timbre/info "starting DB")
        (assoc component :db db :conn conn))))
  (stop [component]
    (if-not (:conn component)
      component
      (let [conn (:conn component)]
        (timbre/info "Shutting down DB")
        (mg/disconnect conn)
        (assoc component :db nil :conn nil)))))

(defn new-db [{:keys [url]}]
  (map->DB {:url url}))

(defn app-roles [role]
  (let [roles {:admin ::admin
               :user ::user}]
    (hash-set (get roles role nil))))

(derive ::admin ::user)

(defn ->keyword [s]
  (keyword (str "cleebo.db/" s)))

(defn new-user [db user & roles]
  (let [db-conn (:db db)
        {:keys [username password]} user
        coll "users"
        roles (or roles [:user])]
    (if (not (mc/find-one-as-map db-conn coll {:username username}))
      (-> (mc/insert-and-return
           db-conn
           coll {:username username
                 :password (hashers/encrypt password {:alg :bcrypt+blake2b-512})
                 :roles (map app-roles roles)})
          (dissoc :password)
          (dissoc :_id)))))

(defn is-user? [db user]
  (let [db-conn (:db db)
        {:keys [username password]} user]
    (boolean (mc/find-one-as-map db-conn "users" {:username username}))))

(defn lookup-user [db username password]
  (let [db-conn (:db db)]
    (if-let [user (mc/find-one-as-map db-conn "users" {:username username})]
      (if (hashers/check password (:password user))
        (-> user
            (update :roles #(into #{} (map ->keyword %)))
            (dissoc :password)
            (dissoc :_id))))))

(defn remove-user [db username]
  (let [db-conn (:db db)]
    (if (is-user? db {:username username})
      (mc/remove db-conn "users" {:username username}))))

;; (def db (component/start (new-db {:url "mongodb://127.0.0.1:27017/cleeboTest"})))
;; (new-user conn {:username "quique" :password "pass"})
;; (remove-user db "quique"))
;; (def conn (mg/connect-via-uri (:database-url env)))
;; (mc/insert (:db conn) "test" {:username "root" :password "bar" :roles #{::user}})
;; (mc/create-index (:db conn) "test" [:username])
;; (mc/indexes-on (:db conn) "test")
;; (mc/find-one-as-map (:db conn) "test" {:username "foo"})
