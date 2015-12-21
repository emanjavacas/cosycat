(ns cleebo.db
  (:require [monger.core :as mg]
            [monger.collection :as mc]
            [monger.operators :refer :all]
            [cemerick.friend.credentials :as creds]
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

(def app-roles
  {:admin ::admin
   :user ::user})

(derive (:admin app-roles) (:user app-roles))

(defn ->keyword [s]
  (keyword (str "cleebo.db/" s)))

(defn new-user [db user & roles]
  (let [db-conn (:db db)
        {:keys [username password]} user
        coll "users"
        roles (or roles [:user])]
    (if (not (mc/find-one-as-map db-conn coll {:username username}))
      (mc/insert-and-return
       db-conn
       coll {:username username
             :password (creds/hash-bcrypt password)
             :roles (map app-roles roles)}))))

(defn is-user? [db user]
  (let [db-conn (:db db)
        {:keys [username password]} user
        coll "users"]
    (boolean (mc/find-one-as-map db-conn coll {:username username}))))

(defn load-user-fn [db]
  (let [db-conn (:db db)
        coll "users"]
    (fn [username]
      (if-let [creds (mc/find-one-as-map db-conn coll {:username username})]
        (-> creds
            (update :password creds/hash-bcrypt)
            (update :roles #(into #{} (map ->keyword %))))))))

(defn remove-user [db username]
  (let [db-conn (:db db)
        coll "users"]
    (if (is-user? db {:username username})
      (mc/remove db-conn coll {:username username}))))

;; (def app-users
;;   {"root" {:username "root"
;;            :password (creds/hash-bcrypt "pass")
;;            :roles (into #{} (select-keys app-roles [:admin]))}
;;    "user" {:username "user"
;;            :password (creds/hash-bcrypt "pass")
;;            :roles (into #{} (select-keys app-roles [:user]))}})

;; (new-user conn {:username "quique" :password "pass"})
;; (new-user conn {:username "root" :password "pass"})
;; (new-user conn {:username "user" :password "pass"})
;; ((load-user-fn conn) "root")
;; (remove-user conn "root")
;; (def conn (mg/connect-via-uri (:database-url env)))
;; (mc/insert (:db conn) "test" {:username "root" :password "bar" :roles #{::user}})
;; (mc/create-index (:db conn) "test" [:username])
;; (mc/indexes-on (:db conn) "test")
;; (mc/find-one-as-map (:db conn) "test" {:username "foo"})
