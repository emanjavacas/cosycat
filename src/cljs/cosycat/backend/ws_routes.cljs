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

(defmethod ws-handler :annotation
  [db {:keys [data]}]
  (re-frame/dispatch [:add-annotation data])
  (update-in db [:session :throbbing?] dissoc))

(defmethod ws-handler :remove-annotation
  [db {{:keys [key span project hit-id]} :data}]
  (re-frame/dispatch [:remove-annotation project hit-id key span])
  db)

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
  (re-frame/dispatch [:register-history [:app-events] {:type type :data user}])
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

(defmethod ws-handler :new-project
  [db {{{creator :creator project-name :name :as project} :project} :data by :by}]
  (let [message (get-msg [:new-project] project-name creator)]
    (re-frame/dispatch [:add-project project])
    (re-frame/dispatch [:notify {:message message :by by}])
    db))

(defmethod ws-handler :project-add-user ;added user gets project data
  [db {{{project-name :name :as project} :project} :data by :by}]
  (re-frame/dispatch [:add-project project])
  (re-frame/dispatch [:notify {:message (get-msg [:new-project] project-name by)}])
  db)

(defmethod ws-handler :project-remove-user
  [db {{:keys [username project-name]} :data}]
  (re-frame/dispatch [:remove-project-user {:username username :project-name project-name}])
  (re-frame/dispatch
   [:notify {:message (str username " has left project " project-name) :by username}])
  db)

(defmethod ws-handler :project-new-user ;existing users get new user data
  [db {{project-name :project-name {role :role username :username :as user} :user} :data by :by}]
  (re-frame/dispatch [:add-project-user {:user user :project-name project-name}])
  (re-frame/dispatch
   [:notify
    {:message (format "\"%s\" has been added to project \"%s\" by \"%s\"" username project-name by)}])
  db)

(defmethod ws-handler :project-remove
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

(defmethod ws-handler :project-update
  [db {{payload :payload project-name :project-name} :data by :by}]
  (re-frame/dispatch [:add-project-update {:project-name project-name :payload payload}])
  (re-frame/dispatch
   [:notify {:message (format "Project \"%s\" has an update by \"%s\"" project-name by)}])
  db)

(defmethod ws-handler :new-project-user-role
  [db {{username :username project-name :project-name role :role} :data by :by}]
  (let [{{me :username} :me} db]
    (re-frame/dispatch [:update-project-user-role project-name username role])
    (re-frame/dispatch
     [:notify
      {:message (format "%s role in project \"%s\" has been changed to \"%s\" by %s"
                        (if (= username me) "Your" username) project-name role by)
       :by (if (= username me) by me)}])
    db))

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
