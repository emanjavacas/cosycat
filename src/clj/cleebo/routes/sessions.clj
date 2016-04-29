(ns cleebo.routes.sessions
  (:require [buddy.auth :refer [authenticated?]]
            [cleebo.routes.auth :refer [safe]]
            [cleebo.components.ws :refer [get-active-users]]
            [cleebo.db.users :refer [user-info users-public-info]]
            [cleebo.db.projects :refer [user-projects]]
            [environ.core :refer [env]]
            [taoensso.timbre :as timbre]))

(defn add-active-info [active-users user]
  (if (contains? active-users (:username user))
    (assoc user :active true)
    (assoc user :active false)))

(defn fetch-init-session
  [{{{username :username roles :roles} :identity} :session
    {db :db ws :ws} :components}]
  (let [init-info (user-info db username)
        users-public (remove #(= username (:username %)) (users-public-info db))
        corpora (env :corpora)
        projects (user-projects db username)
        active-users (get-active-users ws)]
    {:user-info (assoc init-info :projects projects)
     :corpora corpora
     :users (mapv (partial add-active-info active-users) users-public)}))

(def session-route
  (safe (fn [req] {:status 200 :body (fetch-init-session req)})
        {:login-uri "/login" :is-ok? authenticated?}))


