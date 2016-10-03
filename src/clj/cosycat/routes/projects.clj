(ns cosycat.routes.projects
  (:require [compojure.core :refer [routes context POST GET]]
            [cosycat.routes.utils :refer [make-default-route]]
            [cosycat.db.projects :as proj]
            [cosycat.components.ws :refer [send-clients send-client]]
            [taoensso.timbre :as timbre]))

(defn new-project-route
  [{{project-name :project-name desc :description users :users} :params
    {{username :username} :identity} :session
    {db :db ws :ws} :components}]
  (let [project (proj/new-project db username project-name desc users)]
    (send-clients
     ws {:type :new-project :data {:project project}}
     :source-client username
     :target-clients (map :username users))
    project))

(defn update-project-route
  [{{update-payload :payload project-name :project-name :as payload} :params
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
  [{{user :user project-name :project-name} :params
    {{username :username} :identity} :session
    {db :db ws :ws} :components}]
  (let [{:keys [users] :as project} (proj/get-project db username project-name)
        data {:user user :project-name project-name}
        updated-project (proj/add-user db username project-name user)]
    (send-client
     ws (:username user)
     {:type :project-add-user :data {:project updated-project :by username}})
    (send-clients
     ws {:type :project-new-user :data (assoc data :by username)}
     :source-client username
     :target-clients (mapv :username users))
    data))

(defn remove-user-route
  [{{project-name :project-name} :params
    {{username :username} :identity} :session
    {db :db ws :ws} :components}]
  (let [project (proj/get-project db username project-name)]
    (proj/remove-user db username project-name)
    (send-clients
     ws {:type :project-remove-user :data {:username username :project-name project-name}}
     :source-client username
     :target-clients (mapv :username (:users project)))))

(defn remove-project-route
  [{{project-name :project-name} :params
    {{username :username} :identity} :session
    {db :db ws :ws} :components}]
  (let [project (proj/find-project-by-name db project-name)]
    (if-let [project-update (proj/remove-project db username project-name)]
      (let [update-payload (assoc project-update :by username)]
        (send-clients ws {:type :project-update :data update-payload}
         :source-client username
         :target-clients (mapv :username (:users project)))
        update-payload)
      (send-clients ws {:type :project-remove :data {:project-name project-name}}
       :source-client username
       :target-clients (mapv :username (:users project))))))

(defn update-user-role
  [{{project-name :project-name target-username :username new-role :new-role} :params
    {{username :username} :identity} :session
    {db :db ws :ws} :components}]
  (let [project-user (proj/update-user-role db username project-name target-username new-role)]
    (send-clients ws {:type :new-project-user-role
                      :data (assoc project-user :project-name project-name :by username)}
     :source-client username
     :target-clients (mapv :username (:users (proj/find-project-by-name db project-name))))
    project-user))

(defn project-routes []
  (routes
   (context "/project" []
    (POST "/new" [] (make-default-route new-project-route))
    (POST "/update" [] (make-default-route update-project-route))
    (POST "/add-user" [] (make-default-route add-user-route))
    (POST "/remove-user" [] (make-default-route remove-user-route))
    (POST "/remove-project" [] (make-default-route remove-project-route))
    (POST "/update-user-role" [] (make-default-route update-user-role)))))
