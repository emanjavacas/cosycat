(ns cosycat.routes.session
  (:require [cosycat.routes.utils :refer [safe]]
            [cosycat.components.ws :refer [get-active-users]]
            [cosycat.db.users :refer [user-info users-info user-settings]]
            [cosycat.db.projects :refer [get-projects]]
            [config.core :refer [env]]
            [taoensso.timbre :as timbre]))

(defn add-active-info [user active-users]
  (if (contains? active-users (:username user))
    (assoc user :active true)
    (assoc user :active false)))

(defn normalize-users [users username active-users]
  (->> users
       (remove (fn [user] (= username (:username user))))
       (map (fn [user] (dissoc user :settings)))
       (mapv (fn [user] {:username (:username user) :user (add-active-info user active-users)}))))

(defn fetch-init-session
  [{{{username :username roles :roles} :identity} :session
    {db :db ws :ws} :components}]
  (let [active-users (get-active-users ws)
        {:keys [settings] :as me} (user-info db username)]
    {:me (dissoc me :settings)
     :users (normalize-users (users-info db) username active-users)
     :projects (get-projects db username) ;TODO: add project settings
     :settings (or settings {})
     :tagsets (env :tagsets)
     :corpora (env :corpora)}))

(def session-route
  (safe (fn [req] {:status 200 :body (fetch-init-session req)}) {:login-uri "/login"}))
