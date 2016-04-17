(ns cleebo.routes.sessions
  (:require [buddy.auth :refer [authenticated?]]
            [cleebo.routes.auth :refer [safe]]
            [cleebo.db.users :refer [user-info users-public-info]]
            [cleebo.db.projects :refer [user-projects]]
            [environ.core :refer [env]]
            [taoensso.timbre :as timbre]))

(defn fetch-init-session
  [{{{username :username roles :roles} :identity} :session
    {db :db} :components}]
  (let [init-info (user-info db username)
        users-public (remove #(= username (:username %)) (users-public-info db))
        corpora (env :corpora)
        projects (user-projects db username)]
    {:user-info (assoc init-info :projects projects)
     :corpora corpora
     :users users-public}))

(def session-route
  (safe (fn [req] {:status 200 :body (fetch-init-session req)})
        {:login-uri "/login" :is-ok? authenticated?}))


