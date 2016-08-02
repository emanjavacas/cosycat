(ns cleebo.routes.projects
  (:require [compojure.core :refer [routes context POST GET]]
            [cleebo.routes.utils :refer [make-default-route]]
            [cleebo.db.projects :as proj]
            [cleebo.components.ws :refer [send-clients]]
            [taoensso.timbre :as timbre]))

(defn new-project-route
  [{{project-name :project-name desc :description users :users} :params
    {{username :username} :identity} :session
    {db :db ws :ws} :components}]
  (let [project (proj/new-project db username project-name desc users)]
    (proj/)
    (send-clients
     ws {:type :new-project :data project}
     :source-client username
     :target-clients (map :username users))
    project))

(defn remove-project-route
  [{{project-name :project-name} :params
    {{username :username} :identity} :session
    {db :db ws :ws} :components}]
  (let [project (proj/get-project db username project-name)]
    (proj/)
    (send-clients ws {:type :remove-project :data {:project-name project-name}}
     :source-client username
     :target-clients (mapv :username (:users project)))))

(defn update-project-route
  [{{update-payload :payload project-name :project :as payload} :params
    {{username :username} :identity} :session
    {db :db ws :ws} :components}]
  (let [project (proj/get-project db username project-name)]
    (proj/update-project db username project-name update-payload)
    (send-clients
     ws {:type :project-update :data payload}
     :source-client username
     :target-clients (map :username (:users project)))
    payload))

(defn add-user-route
  [{{user :user project-name :project} :params
    {{username :username} :identity} :session
    {db :db ws :ws} :components}]
  (let [{:keys [users] :as project} (proj/get-project db username project-name)
        data {:user user :project project-name}]
    (proj/add-user db username project-name user)
    (send-clients
     ws {:type :project-add-user :data data}
     :source-client username
     :target-clients (conj (mapv :username users) (:username user)))
    data))

(defn remove-user-route
  [{{project-name :project} :params
    {{username :username} :identity} :session
    {db :db ws :ws} :components}]
  (let [project (proj/get-project db username project-name)]
    (proj/remove-user db username project-name)
    (send-clients
     ws {:type :project-remove-user :data {:username username :project project-name}}
     :source-client username
     :target-clients (mapv :username (:users project)))))

(defn project-routes []
  (routes
   (context "/project" []
            (POST "/new" [] (make-default-route new-project-route))
            (POST "/update" [] (make-default-route update-project-route))
            (POST "/add-user" [] (make-default-route add-user-route))
            (POST "/remove-user" [] (make-default-route remove-user-route))
            (POST "/remove-project" [] (make-default-route remove-project-route)))))
