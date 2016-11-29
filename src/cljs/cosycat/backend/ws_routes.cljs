(ns cosycat.backend.ws-routes
  (:require [re-frame.core :as re-frame]
            [cosycat.routes :refer [nav!]]
            [cosycat.backend.ws :refer [send-ws]]
            [cosycat.backend.middleware :refer [standard-middleware]]
            [cosycat.utils :refer [format get-msg]]
            [schema.core :as s]
            [taoensso.timbre :as timbre])
  (:require-macros [cljs.core.match :refer [match]]))

(defmulti ws-handler
  "Main ws handler. dispatches on different routes depending on `dir` (:in :out),
  `type` (:annotation :notify & other-routes) and `status` (:ok :error)"
  (fn [db {:keys [type status]}] type))

;;; Annotations
(defmethod ws-handler :annotation
  [db {:keys [data]}]
  (re-frame/dispatch [:add-annotation data])
  (update-in db [:session :throbbing?] dissoc))

(defmethod ws-handler :remove-annotation
  [db {{:keys [key span project hit-id]} :data}]
  (re-frame/dispatch [:remove-annotation project hit-id key span])
  db)

;;; Auth
(defmethod ws-handler :info
  [db {{message :message} :data by :by}]
  (re-frame/dispatch
   [:notify
    {:message (get-msg [:info] by message) :by by :status :info}])
  db)

(defmethod ws-handler :signup
  [db {{username :username :as user} :data type :type}]
  (re-frame/dispatch [:add-user user])
  (re-frame/dispatch [:notify {:message (get-msg [:signup] username) :by username}])
  db)

(defmethod ws-handler :login
  [db {{username :username :as user} :data}]
  (re-frame/dispatch  [:update-user-active username true])
  (re-frame/dispatch [:notify {:message (get-msg [:login] username) :by username}])
  db)

(defmethod ws-handler :logout
  [db {{username :username :as user} :data}]
  (re-frame/dispatch [:update-user-active username false])
  (re-frame/dispatch [:notify {:message (get-msg [:logout] username) :by username}])
  db)

;;; Projects
;;; Projects general
(defmethod ws-handler :new-project
  [db {{{creator :creator project-name :name :as project} :project} :data by :by}]
  (let [message (get-msg [:new-project] project-name creator)]
    (re-frame/dispatch [:add-project project])
    (re-frame/dispatch [:notify {:message message :by by}])
    db))

(defmethod ws-handler :remove-project
  [db {{:keys [project-name]} :data}]
  (let [active-project (get-in db [:session :active-project])
        message (format "Project \"%s\" was removed" project-name)]
    (re-frame/dispatch [:remove-project project-name])
    (if (= project-name active-project)
      (do (nav! "/")
          (re-frame/dispatch [:open-modal :session-message {:message message}]))
      ;; just a notification
      (re-frame/dispatch [:notify {:message message}])))
  db)

;;; Projects issues
(defmethod ws-handler :new-project-issue
  [db {{issue :issue project-name :project-name} :data by :by}]
  (re-frame/dispatch [:update-project-issue project-name issue])
  (re-frame/dispatch
   [:notify {:message (format "Project \"%s\" has a new issue by \"%s\"" project-name by)
             :by by}])
  db)

(defmethod ws-handler :update-project-issue
  [db {{issue :issue project-name :project-name} :data by :by}]
  (re-frame/dispatch [:update-project-issue project-name issue])
  (re-frame/dispatch
   [:notify {:message (format "Issue in project \"%s\" has an update by \"%s\"" project-name by)
             :by by}])
  db)

(defmethod ws-handler :close-project-issue
  [db {{issue :issue project-name :project-name} :data by :by}]
  (re-frame/dispatch [:update-project-issue project-name issue])
  (re-frame/dispatch
   [:notify {:message (format "\"%s\" has closed an issue in project \"%s\"" by project-name) :by by}])
  db)

;;; Projects users
(defmethod ws-handler :add-project-user ;added user gets project data
  [db {{{project-name :name :as project} :project} :data by :by}]
  (re-frame/dispatch [:add-project project])
  (re-frame/dispatch [:notify {:message (get-msg [:new-project] project-name by)}])
  db)

(defmethod ws-handler :new-project-user ;existing users get new user data
  [db {{project-name :project-name {role :role username :username :as user} :user} :data by :by}]
  (re-frame/dispatch [:add-project-user {:user user :project-name project-name}])
  (re-frame/dispatch
   [:notify
    {:message (format "\"%s\" has been added to project \"%s\" by \"%s\"" username project-name by)}])
  db)

(defmethod ws-handler :remove-project-user
  [db {{:keys [username project-name]} :data}]
  (re-frame/dispatch [:remove-project-user {:username username :project-name project-name}])
  (re-frame/dispatch
   [:notify {:message (str username " has left project " project-name) :by username}])
  db)

(defmethod ws-handler :new-project-user-role
  [db {{username :username project-name :project-name role :role} :data by :by}]
  (let [{{me :username} :me} db
        user-text (if (= username me) "Your" username)
        msg-string "%s role in project \"%s\" has been changed to \"%s\" by %s"]
    (re-frame/dispatch [:update-project-user-role project-name username role])
    (re-frame/dispatch
     [:notify {:message (format msg-string user-text project-name role by) :by by}])
    db))

;;; Projects queries
(defmethod ws-handler :new-query-metadata
  [db {{query :query project-name :project-name} :data by :by}]
  (re-frame/dispatch [:new-query-metadata {:query query :project-name project-name}])
  (let [message (format "Project \"%s\" has new query annotation" project-name)]
    (re-frame/dispatch [:notify {:message message :by by}]))
  db)

(defmethod ws-handler :update-query-metadata
  [db {{:keys [id hit-id status hit-num project-name]} :data by :by}]
  (re-frame/dispatch
   [:update-query-metadata
    {:id id :hit-id hit-id :status status :project-name project-name}])
  (let [message (format "\"%s\" has discarded a hit in a %s's query" by project-name)]
    (re-frame/dispatch [:notify {:message message :by by}]))
  db)

(defmethod ws-handler :drop-query-metadata
  [db {{:keys [id project-name]} :data by :by}]
  (re-frame/dispatch [:drop-query-metadata {:id id :project-name project-name}])
  (let [message (format "A query annotation was dropped in project %s" project-name)]
    (re-frame/dispatch [:notify {:message message :by by}]))
  db)

;;; Users
(defmethod ws-handler :new-user-avatar
  [db {{username :username avatar :avatar} :data}]
  (re-frame/dispatch [:new-user-avatar {:username username :avatar avatar}])
  (let [message (format "%s has changed the avatar" username)]
    (re-frame/dispatch [:notify {:message message :by username}]))
  db)

(defmethod ws-handler :new-user-info
  [db {{:keys [update-map username]} :data}]
  (re-frame/dispatch
   [:notify {:message (format "%s has updated their profile" username) :by username}])
  (re-frame/dispatch [:update-users username update-map])
  db)

(re-frame/register-handler
 :ws
 standard-middleware
 (fn [db [_ {type :type data :data :as payload}]]
   (ws-handler db payload)))
