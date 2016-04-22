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
  [{{type :type {B :B O :O :as scope} :scope} :span reason :reason e :e username :username}]
  (re-frame/dispatch
   [:notify {:message (case type
                        "token" (get-msg [:annotation :error :token] scope e) 
                        "IOB" (get-msg [:annotation :error :IOB] B O e))}]))
(defmethod ann-error-handler cljs.core/PersistentVector
  [{:keys [span reason e username]}]
  (re-frame/dispatch
   [:notify {:message (get-msg [:annotation :error :mult] (count span) e)}]))

(defn ws-handler
  "Main ws handler. dispatches on different routes depending on `dir` (:in :out),
  `type` (:annotation :notify & other-routes) and `status` (:ok :error)"
  [db [_ dir {:keys [type status data payload-id] :as payload}]]
  (match [dir type status]
    [:in :annotation :ok] (do (ann-ok-handler data)
                              (update-in db [:session :throbbing?] dissoc payload-id))
         
    [:in :annotation :error] (do (ann-error-handler data)
                                 (update-in db [:session :throbbing?] dissoc payload-id))
         
    [:out :annotation _] (do (send-ws payload)
                             (assoc-in db [:session :throbbing? payload-id] true))
    ;; info updates
    [:in :notify :info] (let [{:keys [by message]} data]
                          (re-frame/dispatch
                           [:notify
                            {:message (get-msg [:info] by message)
                             :by by
                             :status status}])
                          db)
    
    ;; login updates
    [:in :notify :signup] (let [{username :username} data
                                message (get-msg [:signup] username)]
                            (re-frame/dispatch [:add-user data])
                            (re-frame/dispatch [:notify {:message message}])
                            db)

    [:in :notify :login] (let [{username :username} data
                               message (get-msg [:login] username)]
                           (re-frame/dispatch  [:user-active username true])
                           (re-frame/dispatch [:notify {:message message}])
                           db)

    [:in :notify :logout] (let [{username :username} data
                                message (get-msg [:logout] username)]
                            (re-frame/dispatch [:user-active username false])
                            (re-frame/dispatch [:notify {:message message}])
                            db)     

    ;; project updates
    [:in :notify :new-project] (let [{creator :creator project-name :name :as project} data
                                     message (get-msg [:new-project] project-name creator)]
                                 (re-frame/dispatch [:add-project project])
                                 (re-frame/dispatch [:notify {:message message}])
                                 db)
    [:in :notify :new-user-avatar] (let [{source :source avatar :avatar} data
                                         msg (format "%s has changed the avatar" source)]
                                     (re-frame/dispatch
                                      [:new-user-avatar {:username source :avatar avatar}])
                                     (re-frame/dispatch [:notify {:message msg}]))))



(re-frame/register-handler
 :ws
 standard-middleware
 ws-handler)
