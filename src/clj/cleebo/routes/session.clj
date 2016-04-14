(ns cleebo.routes.session
  (:require [buddy.auth :refer [authenticated?]]
            [cleebo.routes.auth :refer [safe]]
            [cleebo.db.users :refer [user-session]]))

(defn fetch-user-session
  [{{identity :identity} :session
    {db :db} :components}]
  (user-session db identity))

(def session-route
  (safe (fn [req] {:status 200 :body (fetch-user-session req)})
        {:login-uri "/login" :is-ok? authenticated?}))
