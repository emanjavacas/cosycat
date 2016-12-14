(ns cosycat.routes.projects
  (:require [compojure.core :refer [routes context POST GET]]
            [cosycat.app-utils :refer [server-project-name normalize-by]]
            [cosycat.roles :refer [check-annotation-role]]
            [cosycat.utils :refer [assert-ex-info]]
            [cosycat.routes.utils
             :refer [make-default-route make-safe-route ex-user check-user-rights normalize-anns]]
            [cosycat.db.projects :as proj]
            [cosycat.db.annotations :as anns]
            [cosycat.components.ws :refer [send-clients send-client]]
            [config.core :refer [env]]
            [taoensso.timbre :as timbre]))

;;; Checkers
(defn check-new-query-input
  [{{:keys [query-str corpus] :as query-data} :query-data id :id default :default :as params}]
  (cond (empty? id)     [id "Query name can't be empty"]
        (empty? corpus) [corpus "Corpus can't be empty"]))

;;; Exceptions
(defn ex-invalid-input [message input-data]
  (ex-info message {:code :invalid-input :message message :data {:input-data input-data}}))

;;; General
(defn new-project-route
  [{{project-name :project-name desc :description} :params
    {{username :username} :identity} :session
    {db :db ws :ws} :components}]
  (let [project (proj/new-project db username project-name desc)]
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
  (anns/check-sync-by-id db (server-project-name project-name) _id _version)
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
(defmulti close-annotation-issue (fn [db project-name {issue-type :type} issue-action] issue-type))

(defmethod close-annotation-issue "annotation-edit"
  [db project-name {{:keys [hit-id] :as issue-data} :data :as issue} issue-action]
  (when (= issue-action "accepted")
    (let [new-ann (anns/update-annotation db project-name issue-data)]
      {:anns (normalize-anns [new-ann])
       :project-name project-name
       :hit-id hit-id})))

(defmethod close-annotation-issue "annotation-remove"
  [db project-name {{{key :key} :ann hit-id :hit-id span :span :as issue-data} :data} issue-action]
  (when (= issue-action "accepted")
    (do (anns/remove-annotation db project-name issue-data)
        {:key key
         :span span
         :project-name project-name
         :hit-id hit-id})))

(defn close-annotation-edit-route
  [{{project-name :project-name issue-id :issue-id issue-action :action :as params} :params
    {{username :username} :identity} :session
    {db :db ws :ws} :components}]
  (let [{issue-type :type :as issue} (proj/get-project-issue db project-name issue-id)
        {:keys [users]} (proj/find-project-by-name db project-name)
        close-data (select-keys params [:action :comment])
        required-action (case issue-type "annotation-remove" :delete "annotation-edit" :update)]
    (try
      ;; check if user is authorized to execute close
      (check-user-rights db username project-name required-action)
      (let [{:keys [anns] :as ann-payload} (close-annotation-issue db project-name issue issue-action)
            closed-issue (try (proj/close-issue db username project-name issue-id close-data)
                              (catch Throwable e
                                (anns/revert-annotation db project-name (first (vals anns)))
                                (throw e)))]
        ;; send annotation update in case of accepted issue
        (when (= issue-action "accepted")
          (send-clients
           ws {:type (case issue-type
                       "annotation-remove" :remove-annotation
                       "annotation-edit" :annotation)
               :data ann-payload}
           :target-clients (map :username users)))
        ;; send issue update
        (send-clients
         ws {:type :close-project-issue
             :data {:issue closed-issue :project-name project-name}
             :by username}
         :source-client username
         :target-clients (map :username users))
        ;; send closed-issue to source client (and annotation updated in case of accepted issue)
        {:status :ok
         :data (cond-> {:issue-payload closed-issue}
                 (= issue-action "accepted") (assoc :ann-payload ann-payload))})
      (catch clojure.lang.ExceptionInfo e
        (let [{:keys [message data] :as exception} (bean e)
              payload {:message message :data data :status :error}]
          (timbre/error (if (:dev? env) (str exception) (str payload)))
          payload))
      (catch Exception e
        (let [{message :message exception-class :class :as exception} (bean e)
              payload {:message message :data {:exception exception-class} :status :error}]
          (timbre/error (if (:dev? env) (str exception) (str payload)))
          payload)))))

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
  "fetches annotated query hits for a given query. Single annotated query hits can be retrieved
  by also passing a hit-id field"
  [{{{username :username} :identity} :session {db :db} :components
    {project-name :project-name id :id hit-id :hit-id} :params}]
  (check-user-rights db username project-name :read)
  (if hit-id
    (-> (proj/find-query-hit-metadata db project-name id hit-id)
        vector
        (normalize-by :hit-id))
    (-> (proj/find-query-hit-metadata db project-name id)
        (normalize-by :hit-id))))

(defn new-query-metadata-route
  [{{{username :username} :identity} :session {db :db ws :ws} :components
    {{:keys [query-str corpus filter-opts sort-opts] :as query-data} :query-data
     project-name :project-name id :id description :description
     default :default :or {default "unseen"} :as params} :params}]
  (when-let [[message input-data] (check-new-query-input params)]
    (throw (ex-invalid-input message input-data)))
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

(defn drop-query-metadata-route
  [{{{username :username} :identity} :session {db :db ws :ws} :components
    {id :id project-name :project-name} :params}]
  (let [{:keys [users]} (proj/get-project db username project-name)]
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
        (POST "/drop" [] (make-default-route drop-query-metadata-route)))
      (context "/issues" []
        (POST "/new" [] (make-default-route add-project-issue-route))
        (context "/comment" []
          (POST "/new" [] (make-default-route comment-on-project-issue-route))
          (POST "/delete" [] (make-default-route delete-comment-on-project-issue-route))) 
        (context "/annotation" []
          (POST "/open" [] (make-default-route open-annotation-edit-route))
          (POST "/close" [] (make-safe-route close-annotation-edit-route)))))))
