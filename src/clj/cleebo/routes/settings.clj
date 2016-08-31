(ns cleebo.routes.settings
  (:require [compojure.core :refer [routes context POST GET]]
            [cleebo.routes.utils :refer [make-default-route]]
            [cleebo.db.users :as users]
            [cleebo.avatar :refer [user-avatar]]
            [cleebo.components.ws :refer [send-clients]]
            [config.core :refer [env]]
            [taoensso.timbre :as timbre]))

(defn user-settings-route
  [{{{username :username} :identity} :session
    {project-name :project} :params
    {db :db} :components}]
  (users/user-project-settings db username project-name))

(defn new-avatar-route
  [{{{username :username} :identity} :session {db :db ws :ws} :components}]
  (let [avatar (user-avatar username)]
    (users/update-user-info db username {:avatar avatar})
    (send-clients ws {:type :new-user-avatar :data {:avatar avatar :username username}} :source-client username)
    avatar))

(defn save-settings-route
  [{{{username :username} :identity} :session
    {update-map :update-map} :params
    {db :db} :components}]
  (users/update-user-settings db username update-map))

(defn save-project-settings-route
  [{{{username :username} :identity} :session
    {project-name :project update-map :update-map} :params
    {db :db ws :ws} :components}]
  (users/update-user-project-settings db username project-name update-map))

(defn settings-routes []
  (routes
   (context "/settings" []
            (GET "/settings" [] (make-default-route user-settings-route))
            (POST "/new-avatar" [] (make-default-route new-avatar-route))
            (POST "/save-settings" [] (make-default-route save-settings-route))
            (POST "/save-project-settings" [] (make-default-route save-project-settings-route)))))
