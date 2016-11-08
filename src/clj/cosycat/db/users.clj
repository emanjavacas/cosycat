(ns cosycat.db.users
  (:require [monger.collection :as mc]
            [monger.operators :refer :all]
            [buddy.hashers :as hashers]
            [taoensso.timbre :as timbre]
            [schema.core :as s]
            [cosycat.utils :refer [new-uuid]]
            [cosycat.app-utils :refer [not-implemented]]
            [cosycat.db.utils :refer [->set-update-map normalize-user is-user?]]
            [cosycat.schemas.user-schemas :refer [user-schema]]
            [cosycat.components.db :refer [new-db colls]]
            [cosycat.avatar :refer [user-avatar]]))

;;; Exceptions
(defn- ex-user-exists
  "returns a exception to be thrown in case user exists"
  ([data] (ex-info "User already exist" {:code :user-exists
                                         :data data
                                         :message "User already exist"}))
  ([{old-name :username old-email :email} {new-name :username new-email :email}]
   (cond
     (and (= new-name old-name) (= new-email old-email)) (ex-user-exists [:username :email])
     (= new-name old-name) (ex-user-exists :username)
     (= new-email old-email) (ex-user-exists :email))))

(defn- ex-query-exists
  [query-str corpus]
  (ex-info "Query already exists"
           {:code :query-exists
            :data {:query-str query-str :corpus corpus}
            :message "Query already exists"}))

;;; Checkers
(defn- check-user-exists
  "user check. returns nil or ex-info (in case a exception has to be thrown)"
  [{db-conn :db :as db} {:keys [username email] :as new-user}]
  (if-let [old-user (is-user? db new-user)]
    (throw (ex-user-exists old-user new-user))))

(defn- check-query-exists
  [{db-conn :db :as db} username project {:keys [query-str corpus]}]
  (let [project-query-str (format "projects.%s.queries.query-data" project)]
    (when-let [query (mc/find-one-as-map
                      db-conn (:users colls)
                      {:username username
                       (str project-query-str ".query-str") query-str
                       (str project-query-str ".corpus") corpus})]
      (throw (ex-query-exists query-str corpus)))))

;;; Updates
(defn- create-new-user
  "transforms client user payload into a db user doc"
  [{:keys [username password email roles] :as user-payload :or {roles ["user"]}}]
  (let [now (System/currentTimeMillis)]
    (-> user-payload
        (dissoc :csrf)
        (assoc :password (hashers/encrypt password {:alg :bcrypt+blake2b-512}))
        (assoc :roles roles :created now :last-active now :avatar (user-avatar username email)))))

(defn new-user
  "insert user into db"
  ([db user is-admin?]
   (let [update-user (if is-admin? (update user :roles conj "admin") user)]
     (new-user db update-user)))
  ([{db-conn :db :as db} user]
   (check-user-exists db user)
   (-> (mc/insert-and-return db-conn (:users colls) (create-new-user user))
       (normalize-user :settings))))

(defn remove-user
  "remove user from database"           ;TODO: remove all info related to user
  [{db-conn :db :as db} username]
  (mc/remove db-conn (:users colls) {:username username}))

(defn- update-user
  "perform an update based on update-map"
  [{db-conn :db :as db} username update-map]
  (mc/find-and-modify
   db-conn (:users colls)
   {:username username}
   {$set update-map}
   {:return-new true}))

(defn update-user-info
  [db username update-map]
  (check-user-exists db update-map)
  (-> (update-user db username update-map) normalize-user))

(defn user-logout
  "function called on user logout event"
  [{db-conn :db :as db} username]
  (update-user db username {:last-active (System/currentTimeMillis)}))

;;; Fetchers
(defn lookup-user
  "user authentication logic"
  [{db-conn :db :as db} {:keys [username email password] :as user}]
  (if-let [db-user (mc/find-one-as-map
                    db-conn (:users colls)
                    {$or [{:username username} {:email email}]})]
    (when (hashers/check password (:password db-user))
      (-> db-user (normalize-user :settings :projects)))))

(defn- user-info
  "retrieve user client info"
  [{db-conn :db :as db} username]
  (-> (mc/find-one-as-map
       db-conn (:users colls)
       {:username username})
      normalize-user))

(defn user-login-info
  [db username] (user-info db username))

(defn user-public-info
  "retrieve user info as public user"
  [{db-conn :db :as db} username]
  (-> (mc/find-one-as-map
       db-conn (:users colls)
       {:username username})
      (normalize-user :settings :projects)))

(defn get-related-users [{db-conn :db :as db} username]
  (let [projects (->> (mc/find-maps db-conn (:projects colls) {"users.username" username}))]
    (reduce (fn [acc {:keys [users creator]}]            
              (into acc (conj (map :username users) creator)))
            []
            projects)))

(defn users-public-info
  "retrieves all users that interact with user, processed as public users (no private info)"
  [{db-conn :db :as db} username] ;todo, retrieve only users with which users has interactions
  (let [usernames (get-related-users db username)]
    (->> (mc/find-maps db-conn (:users colls) {:username {$in usernames}})
         (map #(normalize-user % :projects :settings)))))

;;; User settings
(defn user-settings
  [{db-conn :db :as db} username]
  (-> (user-info db username)
      (get :settings {})))

(defn update-user-settings
  [{db-conn :db :as db} username update-map]
  ;; do a check on settings
  (-> (update-user db username {:settings update-map})
      (get :settings {})))

(defn user-project-settings
  [db username project-name]
  (-> (user-info db username)
      (get-in [:projects project-name :settings] {})))

(defn update-user-project-settings
  [{db-conn :db} username project-name update-map]
  (-> (mc/find-and-modify
        db-conn (:users colls)
        {:username username}
        {$set (->set-update-map (format "projects.%s.settings" project-name) update-map)}
        {:return-new true})
      (get-in [:projects (keyword project-name) :settings] {})))

;;; User-dependent history
(defn user-project-events
  "find at most `max-events` last user project events that are older than `from`"
  [{db-conn :db :as db} username project & {:keys [from max-events]}]
  (let [from (or from (System/currentTimeMillis))
        project-events-str (format "$projects.%s.events" project)]
    (vec
     (mc/aggregate
      db-conn (:users colls)
      (cond-> [{$match {:username username}}
               {$unwind project-events-str}
               {$project {:data (str project-events-str ".data")
                          :timestamp (str project-events-str ".timestamp")
                          :repeated (str project-events-str ".repeated")
                          :type (str project-events-str ".type")
                          :id (str project-events-str ".id")
                          :_id 0}}
               {$match {:timestamp {$lt from}}}]
        max-events       (into [{$sort {:timestamp -1 :repeated -1}} {$limit max-events}])
        (not max-events) (conj {$sort {:timestamp -1 :repeated -1}}))))))

(defn- register-same-user-project-event
  [{db-conn :db :as db} username project {event-type :type :as event} {event-id :id :as old-event}]
  (let [project-events-str (format "projects.%s.events" project)]
    (mc/update
     db-conn (:users colls)
     {(str project-events-str ".id") event-id}
     {$push {(str project-events-str ".$.repeated") (System/currentTimeMillis)}}
     {:multi false})))

(defn- register-new-user-project-event
  [{db-conn :db :as db} username project event]
  (let [project-events-str (format "projects.%s.events" project)]
    (mc/find-and-modify
     db-conn (:users colls)
     {:username username}
     {$push {project-events-str (assoc event :timestamp (System/currentTimeMillis) :id (new-uuid))}}
     {:return-new false})))

(defn register-user-project-event
  [{db-conn :db :as db} username project {event-type :type event-data :data :as event}]
  (let [{:keys [timestamp type data] :as last} (first (user-project-events db username project :max-events 1))]
    (if (and last (= event-type type) (= data event-data))
      (register-same-user-project-event db username project event last)
      (register-new-user-project-event db username project event))))

;;; Query metadata
(defn find-query-metadata [{db-conn :db :as db} username project {:keys [id query-data]}]
  (let [project-query-str (format "$projects.%s.queries" project)]
    (first
     (mc/aggregate
      db-conn (:users colls)
      [{$match {:username username}}
       {$unwind project-query-str}
       {$project {:id (str project-query-str ".id") :_id 0}}]))))

(defn new-query-metadata
  "Inserts new query into user db to allow for query-related metadata.
   Returns this query's id needed for further updates."
  [{db-conn :db :as db} username project query-data]
  (let [project-query-str (format "projects.%s.queries" project)
        now (System/currentTimeMillis), id (new-uuid)
        payload {:query-data query-data :id id :discarded [] :timestamp now}]
    (check-query-exists db username project query-data)
    (mc/find-and-modify
     db-conn (:users colls)
     {:username username}
     {$push {project-query-str payload}}
     {:return-new true})))

(defn add-query-metadata
  [{db-conn :db :as db} username project {:keys [id discarded] :as payload}]
  (let [project-query-str (format "projects.%s.queries" project)
        new-discard {:hit discarded :timestamp (System/currentTimeMillis)}]
    (mc/update
     db-conn (:users colls)
     {:username username (str project-query-str ".id") id}
     {$push {(str project-query-str ".$.discarded") new-discard}})
    new-discard))

(defn remove-query-metadata
  [{db-conn :db :as db} username project {:keys [id discarded] :as payload}]
  (let [project-query-str (format "projects.%s.queries" project)]
    (mc/find-and-modify
     db-conn (:users colls)
     {:username username (str project-query-str ".id") id}
     {$pull {(str project-query-str ".$.discarded") {"hit" discarded}}}
     {:return-new true})))

(defn drop-query-metadata
  [{db-conn :db :as db} username project query-id]
  (let [project-query-str (format "projects.%s.queries" project)]
    (mc/update
     db-conn (:users colls)
     {:username username}
     {$pull {(str project-query-str) {:id query-id}}})))
