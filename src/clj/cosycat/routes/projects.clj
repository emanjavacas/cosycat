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

(defn add-user-route
  [{{new-username :username role :role project-name :project-name} :params
    {{username :username} :identity} :session
    {db :db ws :ws} :components}]
  (let [new-user {:username new-username :role role}
        {:keys [users] :as project} (proj/add-user db username project-name new-user)]
    (send-client                        ;send to added user
     ws new-username
     {:type :project-add-user :data {:project project} :by username})
    (send-clients                       ;send to project users
     ws {:type :project-new-user :data {:project-name project-name :user new-user} :by username}
     :source-client username
     :target-clients (->> users (map :username) (remove #(= new-username %))))
    {:project-name project-name :user new-user}))

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
      (let [ws-payload {:type :project-issue
                        :data {:project-name project-name :issue delete-payload}
                        :by username}]
        (send-clients ws ws-payload
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

(defn add-project-issue-route
  [{{payload :payload project-name :project-name} :params
    {{username :username} :identity} :session
    {db :db ws :ws} :components}]
  (let [{:keys [users]} (proj/get-project db username project-name)
        issue (proj/add-project-issue db username project-name payload)]    
    (send-clients
     ws {:type :project-update :data {:issue issue :project-name project-name} :by username}
     :source-client username
     :target-clients (map :username users))
    issue))

(defn project-routes []
  (routes
   (context "/project" []
    (POST "/new" [] (make-default-route new-project-route))    
    (POST "/add-user" [] (make-default-route add-user-route))
    (POST "/remove-user" [] (make-default-route remove-user-route))
    (POST "/remove-project" [] (make-default-route remove-project-route))
    (POST "/update-user-role" [] (make-default-route update-user-role))
    (POST "/issue" [] (make-default-route add-project-issue-route)))))
