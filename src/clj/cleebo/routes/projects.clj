(ns cleebo.routes.projects
  (:require [cleebo.routes.utils :refer [make-default-route]]
            [cleebo.db.projects :as proj]
            [cleebo.components.ws :refer [send-clients]]
            [taoensso.timbre :as timbre]))

(defmulti project-router (fn [{{route :route} :params}] route))

(defmethod project-router :new-project
  [{{route :route project-name :name desc :description users :users} :params
    {{username :username} :identity} :session
    {db :db ws :ws} :components}]
  (let [project (proj/new-project db username project-name desc users)]
    (send-clients
     ws {:type :new-project :data project}
     :source-client username
     :target-clients (map :username users))
    project))

(defmethod project-router :project-update
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

(defmethod project-router :add-user
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

(defmethod project-router :remove-user
  [{{project-name :project} :params
    {{username :username} :identity} :session
    {db :db ws :ws} :components}]
  (let [project (proj/get-project db username project-name)]
    (proj/remove-user db username project-name)
    (send-clients
     ws {:type :project-remove-user :data {:username username :project project-name}}
     :source-client username
     :target-clients (mapv :username (:users project)))))

(def project-route (make-default-route project-router))
