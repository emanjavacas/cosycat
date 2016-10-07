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
     ws {:type :new-project :data {:project project} :by username}
     :source-client username
     :target-clients (map :username users))
    project))

(defn update-project-route
  [{{payload :payload project-name :project-name} :params
    {{username :username} :identity} :session
    {db :db ws :ws} :components}]
  (let [{:keys [users]} (proj/get-project db username project-name)]
    (proj/update-project db username project-name payload)
    (send-clients
     ws {:type :project-update :data {:payload payload :project-name project-name} :by username}
     :source-client username
     :target-clients (map :username users))
    payload))

(defn add-user-route
  [{{user :user project-name :project-name} :params
    {{username :username} :identity} :session
    {db :db ws :ws} :components}]
  (let [{:keys [users] :as project} (proj/get-project db username project-name)
        updated-project (proj/add-user db username project-name user)
        added-user-data {:project updated-project}
        client-data {:project-name project-name :user user}]
    (send-client                        ;send user
     ws (:username user)
     {:type :project-add-user :data added-user-data :by username})
    (send-clients
     ws {:type :project-new-user :data client-data :by username}
     :source-client username
     :target-clients (mapv :username users))
    client-data))

(defn remove-user-route
  [{{project-name :project-name} :params
    {{username :username} :identity} :session
    {db :db ws :ws} :components}]
  (let [{:keys [users]} (proj/get-project db username project-name)]
    (proj/remove-user db username project-name)
    (send-clients
     ws {:type :project-remove-user :data {:username username :project-name project-name}}
     :source-client username
     :target-clients (mapv :username users))))

(defn remove-project-route
  [{{project-name :project-name} :params
    {{username :username} :identity} :session
    {db :db ws :ws} :components}]
  (let [{:keys [users]} (proj/find-project-by-name db project-name)]
    (if-let [delete-payload (proj/remove-project db username project-name)]
      (let [client-payload {:type :project-update
                            :data {:project-name project-name :payload delete-payload}
                            :by username}]
        (send-clients ws client-payload
         :source-client username
         :target-clients (mapv :username users))
        delete-payload)
      (send-clients ws {:type :project-remove :data {:project-name project-name}}
       :source-client username
       :target-clients (mapv :username users)))))

(defn update-user-role
  [{{project-name :project-name username :username new-role :new-role} :params
    {{issuer :username} :identity} :session
    {db :db ws :ws} :components}]
  (let [{:keys [users]} (proj/find-project-by-name db project-name)
        project-user (proj/update-user-role db issuer project-name username new-role)
        client-payload {:type :new-project-user-role
                        :data {:username username :project-name project-name :role new-role}
                        :by issuer}]
    (send-clients ws client-payload
     :source-client issuer
     :target-clients (mapv :username users))
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
