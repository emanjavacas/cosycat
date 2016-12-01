(ns cosycat.routes.projects
  (:require [compojure.core :refer [routes context POST GET]]
            [cosycat.app-utils :refer [server-project-name normalize-by]]
            [cosycat.roles :refer [check-annotation-role]]
            [cosycat.routes.utils
             :refer [make-default-route ex-user check-user-rights normalize-anns]]
            [cosycat.vcs :refer [check-sync-by-id]]
            [cosycat.db.projects :as proj]
            [cosycat.db.annotations :as anns]
            [cosycat.components.ws :refer [send-clients send-client]]
            [taoensso.timbre :as timbre]))

;;; General
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

(defn remove-project-route
  [{{project-name :project-name} :params
    {{username :username} :identity} :session
    {db :db ws :ws} :components}]
  (let [{:keys [users]} (proj/find-project-by-name db project-name)]
    (if-let [delete-payload (proj/remove-project db username project-name)]
      (let [ws-payload {:type :new-project-issue
                        :data {:project-name project-name :issue delete-payload}
                        :by username}]
        (send-clients ws ws-payload
         :source-client username
         :target-clients (mapv :username users))
        delete-payload)
      (send-clients ws {:type :remove-project :data {:project-name project-name}}
       :source-client username
       :target-clients (mapv :username users)))))

;;; Issues
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

;;; Issues :new-project-issue
(defn open-annotation-edit-route
  [{{issue-type :type project-name :project-name users :users
     {:keys [_version _id] :as ann-data} :ann-data} :params
    {{username :username} :identity} :session
    {{db-conn :db :as db} :db ws :ws} :components}]
  ;; check the target annotation is on sync
  (check-sync-by-id db-conn (server-project-name project-name) _id _version)
  ;; check annotation has already issue
  (proj/check-annotation-has-issue db project-name _id)
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
     :target-clients (map :username project-users))
    issue))

;;; Issues :update-project-issue
(defn comment-on-project-issue-route
  [{{:keys [comment project-name issue-id parent-id]} :params
    {{username :username} :identity} :session
    {db :db ws :ws} :components}]
  (let [{:keys [users]} (proj/get-project db username project-name)
        issue (proj/comment-on-issue db username project-name issue-id comment :parent-id parent-id)]
    (send-clients
     ws {:type :update-project-issue :data {:issue issue :project-name project-name} :by username}
     :source-client username
     :target-clients (map :username users))
    issue))

(defn delete-comment-on-project-issue-route
  [{{:keys [project-name issue-id comment-id]} :params
    {{username :username} :identity} :session
    {db :db ws :ws} :components}]
  (let [{:keys [users]} (proj/get-project db username project-name)
        issue (proj/delete-comment-on-issue db username project-name issue-id comment-id)]
    (send-clients
     ws {:type :update-project-issue :data {:issue issue :project-name project-name} :by username}
     :source-client username
     :target-clients (map :username users))
    issue))

;;; Issues :close-project-issue
(defmulti resolve-annotation-issue (fn [db project-name {issue-type :type}] issue-type))

(defmethod resolve-annotation-issue "annotation-edit"
  [db project-name {issue-data :data :as issue}]
  (let [{:keys [hit-id] :as new-ann} (anns/update-annotation db project-name issue-data)]
    {:anns (normalize-anns [new-ann]) :project project-name :hit-id hit-id}))

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
     :target-clients (map :username users))
    ;; send issue update
    (send-clients
     ws {:type :close-project-issue
         :data {:issue closed-issue :project-name project-name}
         :by username}
     :source-client username
     :target-clients (map :username users))
    ;; send issue to source client
    closed-issue))

;;; Users
(defn add-user-route
  [{{new-username :username role :role project-name :project-name} :params
    {{username :username} :identity} :session
    {db :db ws :ws} :components}]
  (let [new-user {:username new-username :role role}
        {:keys [users] :as project} (proj/add-user db username project-name new-user)]
    (send-client                        ;send to added user
     ws new-username
     {:type :add-project-user :data {:project project} :by username})
    (send-clients                       ;send to project users
     ws {:type :new-project-user :data {:project-name project-name :user new-user} :by username}
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
     ws {:type :remove-project-user :data {:username username :project-name project-name}}
     :source-client username
     :target-clients (mapv :username users))))

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

;; Query metadata
(defn fetch-query-metadata-route
  [{{{username :username} :identity} :session {db :db} :components
    {project-name :project-name id :id} :params}]
  (check-user-rights db username project-name :read)
  (-> (proj/find-query-hit-metadata db project-name id)
      (normalize-by :hit-id)))

(defn new-query-metadata-route
  [{{{username :username} :identity} :session {db :db ws :ws} :components
    {{:keys [query-str corpus filter-opts sort-opts] :as query-data} :query-data
     project-name :project-name id :id description :description
     default :default :or {default "unseen"}} :params}]
  (let [{:keys [users]} (proj/get-project db username project-name)
        data (proj/new-query-metadata db username project-name id query-data default description)]
    (send-clients
     ws {:type :new-query-metadata
         :data {:query data :project-name project-name}
         :by username}
     :source-client username
     :target-clients (map :username users))
    data))

(defn update-query-metadata-route
  [{{{username :username} :identity} :session {db :db ws :ws} :components
    {:keys [id hit-id status project-name version]} :params}]
  (let [{:keys [users]} (proj/get-project db username project-name)
        payload (if-let [_ (proj/find-query-hit-metadata db project-name id hit-id)]
                  (proj/update-query-metadata db username project-name id hit-id status version)
                  (proj/insert-query-metadata db username project-name id hit-id status))]
    (send-clients
     ws {:type :update-query-metadata
         :data {:query-hit payload
                :query-id id
                :project-name project-name}
         :by username}
     :source-client username
     :target-clients (map :username users))
    payload))

(defn remove-query-metadata-route
  [{{{username :username} :identity} :session {db :db ws :ws} :components
    {:keys [id hit-id project-name version]} :params}]
  (let [{:keys [users]} (proj/get-project db username project-name)
        payload {:project-name project-name :id id :hit-id hit-id}]
    (proj/remove-query-metadata db username project-name id hit-id version)
    (send-clients ws {:type :remove-query-metadata :data payload :by username}
     :source-client username
     :target-clients (map :username users))
    payload))

(defn drop-query-metadata-route
  [{{{username :username} :identity} :session {db :db ws :ws} :components
    {id :id project-name :project-name} :params}]
  (let [{:keys [users]} (proj/get-project db username project-name)]
    (check-user-rights db username project-name :delete)
    (proj/drop-query-metadata db username project-name id)
    (send-clients
     ws {:type :drop-query-metadata
         :data {:id id :project-name project-name}
         :by username}
     :source-client username
     :target-clients (map :username users))))

(defn project-routes []
  (routes
    (context "/project" []
      (POST "/new" [] (make-default-route new-project-route))    
      (POST "/add-user" [] (make-default-route add-user-route))
      (POST "/remove-user" [] (make-default-route remove-user-route))
      (POST "/remove-project" [] (make-default-route remove-project-route))
      (POST "/update-user-role" [] (make-default-route update-user-role))
      (context "/queries" []
        (GET "/fetch" [] (make-default-route fetch-query-metadata-route))
        (POST "/new" [] (make-default-route new-query-metadata-route))
        (POST "/update" [] (make-default-route update-query-metadata-route))
        (POST "/remove" [] (make-default-route remove-query-metadata-route))
        (POST "/drop" [] (make-default-route drop-query-metadata-route)))
      (context "/issues" []
        (POST "/new" [] (make-default-route add-project-issue-route))
        (context "/comment" []
          (POST "/new" [] (make-default-route comment-on-project-issue-route))
          (POST "/delete" [] (make-default-route delete-comment-on-project-issue-route))) 
        (context "/annotation-edit" []
          (POST "/open" [] (make-default-route open-annotation-edit-route))
          (POST "/resolve" [] (make-default-route resolve-annotation-edit-route)))))))
