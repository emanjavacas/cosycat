(ns cosycat.db.projects
  (:require [monger.collection :as mc]
            [monger.operators :refer :all]
            [schema.core :as s]
            [cosycat.vcs :as vcs]
            [cosycat.utils :refer [new-uuid]]
            [cosycat.app-utils :refer [server-project-name get-pending-users dekeyword]]
            [cosycat.schemas.project-schemas
             :refer [project-schema project-user-schema issue-schema queries-schema]]
            [cosycat.roles :refer [check-project-role]]
            [cosycat.db.utils :refer [is-user? normalize-project normalize-query-hit]]
            [cosycat.components.db :refer [new-db colls]]
            [taoensso.timbre :as timbre]))

;;; Exceptions
(defn ex-user-missing [username]
  (let [message "User doesn't exist"]
    (ex-info 
     {:message message
      :code :missing-user
      :data {:username username}})))

(defn ex-project [project-name]
  (let [message "Project already exist"]
    (ex-info message
             {:code :project-exists
              :message message
              :data {:project-name project-name}})))

(defn ex-non-existing-project [project-name]
  (let [message "Project doesn't exist"]
    (ex-info message
             {:code :missing-project
              :message message
              :data {:project-name project-name}})))

(defn ex-user-project [username project-name]
  (let [message "User is not in project"]
    (ex-info message
             {:code :user-not-in-project
              :message message
              :data {:username username :project-name project-name}})))

(defn ex-rights [username action role]
  (let [message (str username " doesn't have sufficient rights")]
    (ex-info message
             {:code :not-authorized
              :message message
              :data {:username username :action action :role role}})))

(defn ex-last-user [project-name]
  (let [message (str "Attempt to remove last user in project: " project-name)]
    (ex-info message
             {:code :last-user-in-project
              :message message
              :data {:project-name project-name}})))

(defn ex-pending [project-name username]
  (let [message (str username "'s role can't be updated because of pending issues")]
    (ex-info message
     {:code :pending-issues
      :message message
      :data {:project-name project-name :username username}})))

(defn ex-user-issue [username issue-id]
  (let [message (str username " is not affected by issue " issue-id)]
    (ex-info message
             {:code :user-not-in-issue
              :message message
              :data {:username username :id issue-id}})))

(defn ex-issue-comment-author [username issue-id comment-id]
  (let [message (str username " is not author of comment")]
    (ex-info message
             {:code :user-not-in-issue
              :message message
              :data {:username username :issue-id issue-id :comment-id comment-id}})))

(defn ex-annotation-has-issue [project-name ann-id]
  (let [message (str "Annotation has already an open issue in project " project-name)]
    (ex-info message
             {:code :annotation-has-issue
              :message message
              :data {:project-name project-name :ann-id ann-id}})))

(defn- ex-query-exists
  [query-str corpus]
  (let [message "Query already exists"]
    (ex-info message
             {:code :query-exists
              :data {:query-str query-str :corpus corpus}
              :message message})))

(defn- ex-cannot-drop-query
  [username query-id]
  (let [message (format "%s cannot drop query %s" username query-id)]
    (ex-info message
             {:code :cannot-drop-query
              :data {:id query-id}
              :message message})))

;;; Checkers
(declare get-project-issue find-project-by-name get-user-role find-query-metadata)

(defn is-project? [db project-name]
  (boolean (find-project-by-name db project-name)))

(defn is-authorized?
  ([{users :users :as project} username action]
  (if-let [{role :role} (first (filter #(= username (:username %)) users))]
    (check-project-role action role)))
  ([{db-conn :db :as db} project-name username action]
   (let [project (find-project-by-name db project-name)]
     (is-authorized? project username action))))

(defn has-pending-issue? [{:keys [issues] :as project} username]
  (some (fn [{issue-users :users status :status :as issue}]
          (and (= status "open") (contains? (apply hash-set issue-users) username)))
        issues))

(defn check-new-project [db project-name & [users]]
  (let [missing-username (some #(when (not (is-user? db {:username %})) %) (map :username users))]
    (cond
      missing-username (throw (ex-user-missing missing-username))
      (is-project? db project-name) (throw (ex-project project-name)))))

(defn check-user-in-project [db username project-name]
  (if-not (is-authorized? db project-name username :read)
    (throw (ex-user-project username project-name))))

(defn check-project-empty
  [db project-name & {:keys [transform-f] :or {transform-f identity}}]
  (let [project (find-project-by-name db project-name)]
    (if-let [users (seq (-> project transform-f :users))]
      :ok
      (throw (ex-last-user project-name)))))

(defn check-user-is-comment-author
  [{db-conn :db :as db} project-name username issue-id comment-id]
  (let [{{{:keys [by]} (keyword comment-id)} :comments} (get-project-issue db project-name issue-id)]
    (when-not (= by username)
      (throw (ex-issue-comment-author username issue-id comment-id)))))

(defn check-annotation-has-issue [{db-conn :db :as db} project-name ann-id]
  (when (->> (mc/find-one-as-map
              db-conn (:projects colls)
              {:name project-name
               "issues.status" "open"
               "issues.data._id" ann-id}))
    (throw (ex-annotation-has-issue project-name ann-id))))

(defn check-query-exists
  [{db-conn :db :as db} project-name query-id {:keys [query-str corpus filter-opts sort-opts]}]
  (when-let [query (mc/find-one-as-map
                    db-conn (:projects colls)
                    {:name project-name "queries.id" query-id})]
    (throw (ex-query-exists query-str corpus))))

(defn check-user-is-query-metadata-creator
  [{db-conn :db :as db} username project-name query-id]
  (if-let [{:keys [creator]} (find-query-metadata db project-name query-id)]
    (when-not (= username creator)
      (throw (ex-cannot-drop-query username query-id)))))

;;; Getters
(defn find-project-by-name [{db-conn :db} project-name]
  (mc/find-one-as-map db-conn (:projects colls) {:name project-name}))

(defn get-user-role
  ([project username]
   (some (fn [{user :username role :role}] (when (= user username) role)) (:users project)))
  ([{db-conn :db :as db} project-name username]
   (let [project (find-project-by-name db project-name)]
     (get-user-role project username))))

(defn get-project
  "retrieves project by name"
  [{db-conn :db :as db} username project-name]
  (check-user-in-project db username project-name)
  (-> (mc/find-one-as-map db-conn (:projects colls) {"name" project-name})
      normalize-project))

(defn get-projects
  "retrieves data of projects in which `username` is involved"
  [{db-conn :db} username]
  (->> (mc/find-maps db-conn (:projects colls) {"users.username" username})
       (mapv normalize-project)))

(defn project-events
  "find at most `max-events` last project events that are older than `from`"
  [{db-conn :db :as db} username project & {:keys [from max-events]}]
  (let [from (or from (System/currentTimeMillis))]
    (check-user-in-project db username project)
    (vec (mc/aggregate
          db-conn (:projects colls)
          (cond-> [{$match {:name project}}
                   {$unwind "$events"}
                   {$project {:data "$events.data"
                              :timestamp "$events.timestamp"
                              :type "$events.type"
                              :id "$events.id"
                              :_id 0}}
                   {$match {:timestamp {$lt from}}}]
            max-events       (into [{$sort {:timestamp -1}} {$limit max-events}])
            (not max-events) (conj {$sort {:timestamp -1}}))))))

;;; Events
(defn make-event [event-map]
  (assoc event-map :id (new-uuid) :timestamp (System/currentTimeMillis)))

(defn new-user-event [username]
  (make-event {:type :new-user-in-project :data {:username username}}))

(defn new-user-role-event [username new-role]
  (make-event {:type :new-user-role :data {:username username :new-role new-role}}))

(defn remove-user-event [username]
  (make-event {:type :user-left-project :data {:username username}}))

;;; Setters
(defn join-project-creator [creator users]
  (conj users {:username creator :role "creator"}))

(defn create-project-index [{db-conn :db :as db} project-name]
  (mc/create-index
   db-conn (server-project-name project-name)
   (array-map "ann.key" 1 :span 1 :corpus 1) ;; unique identifiers for annotations
   {:unique true}))

(defn new-project
  "creates a new project"
  [{db-conn :db :as db} creator project-name description & [users]]
  (check-new-project db project-name users)
  (create-project-index db project-name)
  (-> (mc/insert-and-return
       db-conn (:projects colls)
       {:name project-name
        :description description
        :created (System/currentTimeMillis)
        :creator creator
        :users (->> users (join-project-creator creator) (map #(select-keys % [:username :role])))})
      normalize-project))

(defn add-user
  "adds user to project"
  [{db-conn :db :as db} issuer project-name {username :username :as user}]
  (check-user-in-project db issuer project-name)
  (-> (mc/find-and-modify
       db-conn (:projects colls)
       {"name" project-name}
       ;; atomically add event to project
       {$push {"users" user "events" (new-user-event username)}}
       {:return-new true})
      normalize-project))

(defn update-user-role [{db-conn :db :as db} issuer project-name username new-role]
  (let [project (find-project-by-name db project-name)
        role (get-user-role db project-name issuer)]
    (when-not role
      (throw (ex-user-missing username)))
    (when (has-pending-issue? project username)
      (throw (ex-pending project-name username)))
    (when-not (check-project-role :write role)
      (throw (ex-rights issuer :write role)))
    (->> (mc/find-and-modify
          db-conn (:projects colls)
          {"users.username" username "name" project-name}
          {$set {"users.$.role" new-role}
           ;; atomically add event to project
           $push {"events" (new-user-role-event username new-role)}}
          {:return-new true})
         :users
         (filter #(= username (:username %)))
         first)))

(defn- remove-from-users [users username]
  (remove #(= username (:username %)) users))

(defn remove-user
  "removes user from project"
  [{db-conn :db :as db} username project-name]
  (check-user-in-project db username project-name)
  (check-project-empty db project-name :transform-f #(update % :users remove-from-users username))
  (mc/update
   db-conn (:projects colls)
   {"name" project-name}
   {$pull {"users" {"username" username}}
    ;; atomically add event to project
    $push {"events" (remove-user-event username)}}))

;;; Issues
(defn get-project-issue [{db-conn :db :as db} project-name issue-id]
  (-> (mc/find-one-as-map
       db-conn (:projects colls)
       {:name project-name
        "issues.id" issue-id}
       {"issues.$.id" 1})
      :issues
      first))

(defn find-annotation-issue [{db-conn :db :as db} project-name id & {:keys [status]}]
  (-> (mc/find-one-as-map
       db-conn (:projects colls)
       (cond-> {:name project-name
                "issues.type" {$in ["annotation-edit" "annotation-remove"]}
                "issues.data._id" id}
         (not (nil? status)) (assoc "issues.status" status))
       {"issues.$.type" 1})
      :issues
      first))

(defn find-delete-project-issue [{db-conn :db :as db} project-name]
  (-> (mc/find-one-as-map
       db-conn (:projects colls)
       {:name project-name
        "issues.type" "delete-project-agree"}
       {"issues.$.type" 1})
      :issues
      first))

(defn add-project-issue
  "adds issue to `project.issues`"
  [{db-conn :db :as db} username project-name issue-payload]
  (check-user-in-project db username project-name)
  (s/validate (dissoc issue-schema :id) issue-payload)
  (let [id (new-uuid)]
    (->> (mc/find-and-modify
          db-conn (:projects colls)
          {:name project-name}
          {$push {:issues (assoc issue-payload :id id)}}
          {:return-new true
           :fields {:issues 1}})
         :issues
         (filter #(= id (:id %)))
         first)))

(defn- update-project-issue
  "utility function for operations on issues. Accepts a `query-map` to allow for modifications
   in other nested arrays such as :comments"
  ([{db-conn :db :as db} username project-name issue-id update-map]
   (update-project-issue db username project-name issue-id {} update-map))
  ([{db-conn :db :as db} username project-name issue-id query-map update-map]
   (->> (mc/find-and-modify db-conn (:projects colls)
                            (merge query-map {:name project-name "issues.id" issue-id})
                            update-map
                            {:return-new true :fields {:issues 1}})
        :issues
        (filter #(= issue-id (:id %)))
        first)))

(defn comment-on-issue [db username project-name issue-id comment & {:keys [parent-id]}]
  (let [timestamp (System/currentTimeMillis), id (new-uuid)
        comment-map {:by username :comment comment :timestamp timestamp :id id}]
    (check-user-in-project db username project-name)
    (update-project-issue
     db username project-name issue-id
     (cond-> {$set {(str "issues.$.comments." id) comment-map}}
       parent-id (assoc $push {(format "issues.$.comments.%s.children" parent-id) id})))))

(defn delete-comment-on-issue [db username project-name issue-id comment-id]
  (check-user-is-comment-author db project-name username issue-id comment-id)
  (update-project-issue
   db username project-name issue-id
   {$set {(format "issues.$.comments.%s.deleted" comment-id) true}}))

(defn close-issue [db username project-name issue-id {:keys [action comment]}]
  (update-project-issue
   db username project-name issue-id
   {$set {"issues.$.status" "closed"
          "issues.$.resolve" (cond-> {:status action
                                      :timestamp (System/currentTimeMillis)
                                      :by username}
                               comment (assoc :comment comment))}}))

(defn set-user-delete-agree
  [db username project-name issue-id]
  (update-project-issue
   db username project-name issue-id
   {$push {"issues.$.data.agreed" username}}))

(defn make-delete-issue [username]
  {:type "delete-project-agree"
   :status "open"
   :data {:agreed [username]}
   :by username
   :users "all"
   :timestamp (System/currentTimeMillis)})

(declare drop-query-hit-metadata)

(defn erase-project
  "drops the project annotations and removes the project info from users"
  [{db-conn :db :as db} project-name]
  (let [{:keys [users queries]} (mc/find-one-as-map db-conn (:projects colls) {:name project-name})]
    ;; drop project annotation database
    (vcs/drop db-conn (server-project-name project-name))
    ;; drop project from projects collection
    (mc/remove db-conn (:projects colls) {:name project-name})
    ;; remove project from
    (mc/update db-conn (:users colls) {:name users} {$pull {:projects {:name project-name}}})
    ;; remove query-metadata from query
    (doseq [{query-id :id} queries]
      (drop-query-hit-metadata db project-name query-id))
    ;; TODO: remove user.projects.project-name (events, settings)?)
  nil))

(defn remove-project
  "drops the collections and removes project from users info (as per `erase-project`)
   if all users agree to delete the project, otherwise adds user to agreeing"
  [{db-conn :db :as db} username project-name]
  (let [{:keys [users] :as project} (find-project-by-name db project-name)
        role (get-user-role project username)]
    (when-not project
      (throw (ex-non-existing-project project-name)))
    (when-not (check-project-role :delete role)
      (throw (ex-rights username :delete role)))
    (let [issue (if-let [{issue-id :id} (find-delete-project-issue db project-name)]
                  (set-user-delete-agree db username project-name issue-id)
                  (add-project-issue db username project-name (make-delete-issue username)))
          {:keys [pending-users NA-users agreed-users]} (get-pending-users issue users)]
      (if (empty? pending-users)
        (do (timbre/info "Erasing project" project-name) (erase-project db project-name))
        (do (timbre/info "Pending users to project remove" project-name) issue)))))

;;; Query metadata
(defn get-hit-meta-id [{:keys [project-name query-id hit-id]}]
  (apply str (interpose "." [project-name query-id hit-id])))

(defn find-query-metadata
  "returns query metadata (excluding actual query hit metadata)"
  [{db-conn :db :as db} project-name query-id]
  (-> (mc/find-one-as-map
       db-conn (:projects colls)
       {:name project-name
        "queries.id" query-id}
       {"queries.$.id" 1})
      :queries
      first))

(defn find-query-hit-metadata
  ([{db-conn :db :as db} project-name query-id]
   (->> (mc/find-maps db-conn (:queries colls) {:project-name project-name :query-id query-id})
        (mapv normalize-query-hit)))
  ([{db-conn :db :as db} project-name query-id hit-id]
   (let [id (get-hit-meta-id {:project-name project-name :query-id query-id :hit-id hit-id})]
     (-> (mc/find-one-as-map db-conn (:queries colls) {:_id id})
         normalize-query-hit))))

(defn ensure-query-index [db]
  (mc/ensure-index
   db (:queries colls)
   (array-map :query-id 1 :project-name 1 :hit-id 1)
   {:unique true}))

(defn new-query-metadata
  "Inserts new query into user db to allow for query-related metadata.
   Returns this query's id needed for further updates."
  [{db-conn :db :as db} username project-name query-id query-data query-default description]
  (s/validate (:query-data queries-schema) query-data)
  (check-user-in-project db username project-name)
  (ensure-query-index db-conn)
  (let [payload {:query-data query-data
                 :id query-id
                 :default query-default
                 :description description
                 :timestamp (System/currentTimeMillis)
                 :creator username}]
    (check-query-exists db project-name query-id query-data)
    (mc/find-and-modify
     db-conn (:projects colls)
     {:name project-name}
     {$push {:queries payload}}
     {:return-new false})
    payload))

(defn insert-query-metadata
  "Insert annotation metadata for a new hit"
  [{db-conn :db :as db} username project-name query-id hit-id status]
  (let [id (get-hit-meta-id {:project-name project-name :query-id query-id :hit-id hit-id})]
    (-> (vcs/insert-and-return
            db-conn (:queries colls)
            {:project-name project-name
             :query-id query-id
             :hit-id hit-id
             :status status
             :timestamp (System/currentTimeMillis)
             :by username
             :_id id})
        normalize-query-hit)))

(defn update-query-metadata
  "Upsert new query hit data or modify a given hit-status "
  [{db-conn :db :as db} username project-name query-id hit-id status version]
  (check-user-in-project db username project-name)
  (let [id (get-hit-meta-id {:project-name project-name :query-id query-id :hit-id hit-id})]
    (-> (vcs/find-and-modify
         db-conn (:queries colls) version {:_id id}
         {$set {:status status :hit-id hit-id :timestamp (System/currentTimeMillis) :by username}}
         {:return-new true})
        normalize-query-hit)))

(defn drop-query-hit-metadata
  "drop query hits related with a given query (also remove any eventual vcs docs)"
  [{db-conn :db :as db} project-name query-id]
  (vcs/bulk-remove db-conn {:query-id query-id})
  (mc/remove db-conn (:queries colls) {:project-name project-name :query-id query-id}))

(defn drop-query-metadata
  "drop query metadata and hits related to it as per `drop-query-hit-metadata`"
  [{db-conn :db :as db} username project-name query-id]
  (check-user-in-project db username project-name)
  (check-user-is-query-metadata-creator db username project-name query-id)
  ;; remove query from project
  (mc/update
   db-conn (:projects colls)
   {:name project-name}
   {$pull {:queries {:id query-id}}})
  ;; remove query-metadata from queries collections
  (drop-query-hit-metadata db project-name query-id))
