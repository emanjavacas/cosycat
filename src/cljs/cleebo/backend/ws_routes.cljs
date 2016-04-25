(ns cleebo.backend.ws-routes
  (:require [re-frame.core :as re-frame]
            [cleebo.backend.ws :refer [send-ws]]
            [cleebo.backend.middleware :refer [standard-middleware]]
            [cleebo.utils :refer [format get-msg]]
            [schema.core :as s]
            [taoensso.timbre :as timbre])
  (:require-macros [cljs.core.match :refer [match]]))

(defmulti ann-ok-handler
  "Variadic ws handler for successfull annotations. Dispatches are based on whether
  ann-map is a vector (bulk annotation payload) or a map (single annotation payload)"
  (fn [{:keys [ann-map hit-id] :as data}] (type ann-map)))
(defmethod ann-ok-handler cljs.core/PersistentArrayMap
  [{hit-id :hit-id {{{B :B O :O :as scope} :scope type :type} :span :as ann} :ann-map}]
  (re-frame/dispatch [:add-annotation {:hit-id hit-id :ann-map ann}]))
(defmethod ann-ok-handler cljs.core/PersistentVector
  [{:keys [hit-id ann-map]}]
  (doseq [[ann-map hit-id] (map vector ann-map hit-id)]
    (re-frame/dispatch [:add-annotation {:hit-id hit-id :ann-map ann-map}])))

(defmulti ann-error-handler
  "Variadic ws handler for unsuccessfull annotations. Dispatches are based on whether
  span is a vector (bulk annotation payload) or a map (sinlge annotation payload)"
  (fn [{span :span :as data}] (type span)))
(defmethod ann-error-handler cljs.core/PersistentArrayMap
  [{{type :type {B :B O :O :as scope} :scope} :span reason :reason e :e}]
  (re-frame/dispatch
   [:notify {:message (case type
                        "token" (get-msg [:annotation :error :token] scope e) 
                        "IOB" (get-msg [:annotation :error :IOB] B O e))}]))
(defmethod ann-error-handler cljs.core/PersistentVector
  [{:keys [span reason e username]}]
  (re-frame/dispatch
   [:notify {:message (get-msg [:annotation :error :mult] (count span) e)}]))

(defmulti ws-handler
  "Main ws handler. dispatches on different routes depending on `dir` (:in :out),
  `type` (:annotation :notify & other-routes) and `status` (:ok :error)"
  (fn [db dir {:keys [type status]}] [dir type status]))

(defmethod ws-handler [:in :annotation :ok]
  [db _ {:keys [data payload-id]}]
  (ann-ok-handler data)
  (update-in db [:session :throbbing?] dissoc payload-id))

(defmethod ws-handler [:in :annotation :error]
  [db _ {:keys [data payload-id]}]
  (ann-error-handler data)
  (update-in db [:session :throbbing?] dissoc payload-id))

(defmethod ws-handler [:out :annotation :ok]
  [db _ {:keys [payload-id] :as payload}]
  (send-ws payload)
  (assoc-in db [:session :throbbing? payload-id] true))

(defmethod ws-handler [:in :notify :info]
  [db _ {{:keys [by message]} :data status :status}]
  (re-frame/dispatch
   [:notify
    {:message (get-msg [:info] by message)
     :by by
     :status status}])
  db)

(defmethod ws-handler [:in :notify :signup]
  [db _ {{username :username :as data} :data}]
  (re-frame/dispatch [:add-user data])
  (re-frame/dispatch [:notify {:message (get-msg [:signup] username) :by username}])
  db)

(defmethod ws-handler [:in :notify :login]
  [db _ {{username :username :as data} :data}]
  (re-frame/dispatch  [:user-active username true])
  (re-frame/dispatch [:notify {:message (get-msg [:login] username) :by username}])
  db)

(defmethod ws-handler [:in :notify :logout]
  [db _ {{username :username :as data} :data}]
  (re-frame/dispatch [:user-active username false])
  (re-frame/dispatch [:notify {:message (get-msg [:logout] username) :by username}])
  db)

(defmethod ws-handler [:in :notify :new-project]
  [db _ {{creator :creator project-name :name :as project} :data}]
  (let [message (get-msg [:new-project] project-name creator)]
    (re-frame/dispatch [:add-project project])
    (re-frame/dispatch [:notify {:message message}])
    db))

(defmethod ws-handler [:in :notify :new-user-avatar]
  [db _ {{username :username avatar :avatar} :data}]
  (re-frame/dispatch [:new-user-avatar {:username username :avatar avatar}])
  (when-not (= username (get-in db [:session :user-info :username]))
    (let [message (format "%s has changed the avatar" username)]
      (re-frame/dispatch [:notify {:message message :by username}])))
  db)

(re-frame/register-handler
 :ws
 standard-middleware
 (fn [db [_ dir payload]]
   (ws-handler db dir payload)))
