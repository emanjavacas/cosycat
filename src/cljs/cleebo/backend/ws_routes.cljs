(ns cleebo.backend.ws-routes
  (:require [re-frame.core :as re-frame]
            [cleebo.backend.ws :refer [send-ws]]
            [cleebo.backend.middleware :refer [standard-middleware]]
            [cleebo.utils :refer [format get-msg]]
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

(defmethod ws-handler :info
  [db {{:keys [by message]} :data status :status}]
  (re-frame/dispatch
   [:notify
    {:message (get-msg [:info] by message)
     :by by
     :status status}])
  db)

(defmethod ws-handler :signup
  [db {{username :username :as data} :data}]
  (re-frame/dispatch [:add-user data])
  (re-frame/dispatch [:notify {:message (get-msg [:signup] username) :by username}])
  db)

(defmethod ws-handler :login
  [db {{username :username :as data} :data}]
  (re-frame/dispatch  [:update-user-active username true])
  (re-frame/dispatch [:notify {:message (get-msg [:login] username) :by username}])
  db)

(defmethod ws-handler :logout
  [db {{username :username :as data} :data}]
  (re-frame/dispatch [:update-user-active username false])
  (re-frame/dispatch [:notify {:message (get-msg [:logout] username) :by username}])
  db)

(defmethod ws-handler :new-project
  [db {{creator :creator project-name :name :as project} :data}]
  (let [message (get-msg [:new-project] project-name creator)]
    (re-frame/dispatch [:add-project project])
    (re-frame/dispatch [:notify {:message message}])
    db))

(defmethod ws-handler :new-user-avatar
  [db {{username :username avatar :avatar} :data}]
  (re-frame/dispatch [:new-user-avatar {:username username :avatar avatar}])
  (when-not (= username (get-in db [:session :user-info :username]))
    (let [message (format "%s has changed the avatar" username)]
      (re-frame/dispatch [:notify {:message message :by username}])))
  db)

(re-frame/register-handler
 :ws
 standard-middleware
 (fn [db [_ payload]]
   (re-frame/dispatch [:register-history [:server-events] {:type :ws :payload payload}])
   (ws-handler db payload)))
