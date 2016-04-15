(ns cleebo.routes.projects
  (:require [buddy.auth :refer [authenticated?]]
            [cleebo.routes.auth :refer [safe]]
            [cleebo.db.projects :refer [user-projects new-project]]
            [taoensso.timbre :as timbre]))

(def project-route
  (safe (fn [{{route :route name :name desc :description users :users} :params
              {{username :username} :identity} :session
              {db :db} :components :as req}]
          (let [body (case route
                       :new-project (new-project db username name desc users)
                       :update-project (throw (ex-info "not implemented yet"))
                       (throw (ex-info "unidentified project route" {:route route})))]
            {:status 200 :body body}))
        {:login-uri "/login" :is-ok? authenticated?}))
