(ns cosycat.routes.projects
  (:require [compojure.core :refer [routes context POST GET]]
            [cosycat.app-utils :refer [server-project-name]]
            [cosycat.roles :refer [check-annotation-role]]
            [cosycat.routes.utils
             :refer [make-default-route ex-user check-user-rights normalize-anns]]
            [cosycat.vcs :refer [check-sync-by-id]]
            [cosycat.db.projects :as proj]
            [cosycat.db.annotations :as anns]
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
     ws {:type :new-project-issue :data {:issue issue :project-name project-name} :by username}
     :source-client username
     :target-clients (map :username users))
    issue))

(defn comment-on-project-issue-route
  [{{:keys [comment project-name issue-id parent-id]} :params
    {{username :username} :identity} :session
    {db :db ws :ws} :components}]
  (let [{:keys [users]} (proj/get-project db username project-name)
        updated-issue (proj/comment-on-issue db username project-name issue-id comment :parent-id parent-id)]
    (send-clients
     ws {:type :update-project-issue :data {:issue updated-issue :project-name project-name} :by username}
     :source-client username
     :target-clients (map :username users))
    updated-issue))

(defn open-annotation-edit-route
  [{{issue-type :type project-name :project-name users :users
     {:keys [_version _id] :as ann-data} :ann-data} :params
    {{username :username} :identity} :session
    {{db-conn :db :as db} :db ws :ws} :components}]
  ;; check the target annotation is on sync
  (check-sync-by-id db-conn (server-project-name project-name) _id _version)
  (let [issue-payload {:by username
                       :type issue-type
                       :timestamp (System/currentTimeMillis)
                       :status "open"
                       :users users
                       :data (assoc ann-data :username username)} ;match update-annotation signature
        {project-users :users} (proj/get-project db username project-name)
        issue (proj/add-project-issue db username project-name issue-payload)]
    (send-clients
     ws {:type :new-project-issue :data {:issue issue :project-name project-name} :by username}
     :source-client username
     :target-clients project-users)
    issue))

(defmulti resolve-annotation-issue (fn [db project-name {issue-type :type}] issue-type))

(defmethod resolve-annotation-issue "annotation-edit"
  [db project-name {issue-data :data :as issue}]
  (let [{:keys [hit-id] :as new-ann} (anns/update-annotation db project-name issue-data)]
    {:anns (normalize-anns new-ann) :project project-name :hit-id hit-id}))

(defn resolve-annotation-edit-route
  [{{project-name :project-name action :action issue-id :issue-id} :params
    {{username :username} :identity} :session
    {db :db ws :ws} :components}]
  ;; check if user is authorized to execute resolve
  (let [issue (proj/get-project-issue db project-name issue-id)
        {:keys [users]} (proj/find-project-by-name db project-name)
        ann-payload (resolve-annotation-issue db project-name issue)
        closed-issue (proj/close-issue db username project-name issue-id)]
    ;; send annotation update
    (send-clients
     ws {:type :annotation :data ann-payload}
     :target-clients users)
    ;; send issue update
    (send-clients
     ws {:type :close-project-issue :data {:issue closed-issue :project-name project-name} :by username}
     :source-client username
     :target-clients users)
    ;; send issue to source client
    closed-issue))

(defn project-routes []
  (routes
   (context "/project" []
    (POST "/new" [] (make-default-route new-project-route))    
    (POST "/add-user" [] (make-default-route add-user-route))
    (POST "/remove-user" [] (make-default-route remove-user-route))
    (POST "/remove-project" [] (make-default-route remove-project-route))
    (POST "/update-user-role" [] (make-default-route update-user-role))
    (context "/issues" []
     (POST "/new" [] (make-default-route add-project-issue-route))
     (POST "/comment" [] (make-default-route comment-on-project-issue-route))
      (context "/annotation-edit" []
       (POST "/open" [] (make-default-route open-annotation-edit-route))
       (POST "/resolve" [] (make-default-route resolve-annotation-edit-route)))))))
