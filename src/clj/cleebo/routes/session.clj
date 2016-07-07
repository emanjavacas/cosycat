(ns cleebo.routes.session
  (:require [buddy.auth :refer [authenticated?]]
            [cleebo.routes.auth :refer [safe]]
            [cleebo.components.ws :refer [get-active-users]]
            [cleebo.db.users :refer [user-info users-info]]
            [cleebo.db.projects :refer [get-projects]]
            [config.core :refer [env]]
            [taoensso.timbre :as timbre]))

(defn add-active-info [user active-users]
  (if (contains? active-users (:username user))
    (assoc user :active true)
    (assoc user :active false)))

(defn normalize-users [users username active-users]
  (->> users
       (remove (fn [user] (= username (:username user))))
       (mapv (fn [user] {:username (:username user) :user (add-active-info user active-users)}))))

(defn fetch-init-session
  [{{{username :username roles :roles} :identity} :session
    {db :db ws :ws} :components}]
  (let [active-users (get-active-users ws)]
    {:me (user-info db username)
     :users (normalize-users (users-info db) username active-users)
     :projects (get-projects db username)
     :corpora (env :corpora)}))

(def session-route
  (safe (fn [req] {:status 200 :body (fetch-init-session req)})
        {:login-uri "/login" :is-ok? authenticated?}))
