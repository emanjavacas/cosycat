(ns cleebo.routes.sessions
  (:require [buddy.auth :refer [authenticated?]]
            [cleebo.routes.auth :refer [safe]]
            [cleebo.components.ws :refer [get-active-users]]
            [cleebo.db.users :refer [user-info users-info]]
            [cleebo.db.projects :refer [get-projects]]
            [environ.core :refer [env]]
            [taoensso.timbre :as timbre]))

(defn add-active-info [active-users user]
  (if (contains? active-users (:username user))
    (assoc user :active true)
    (assoc user :active false)))

(defn fetch-init-session
  [{{{username :username roles :roles} :identity} :session
    {db :db ws :ws} :components}]
  (let [me (user-info db username)
        rest-users (remove #(= username (:username %)) (users-info db))
        corpora (env :corpora)
        projects (get-projects username)]
    {:me me
     :users (mapv (partial add-active-info (get-active-users ws)) rest-users)
     :projects (get-projects db username)
     :corpora corpora}))

(def session-route
  (safe (fn [req] {:status 200 :body (fetch-init-session req)})
        {:login-uri "/login" :is-ok? authenticated?}))
