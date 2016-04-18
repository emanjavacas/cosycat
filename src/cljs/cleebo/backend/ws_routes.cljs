(ns cleebo.backend.ws-routes
  (:require [re-frame.core :as re-frame]
            [cleebo.backend.ws :refer [send-ws]]
            [cleebo.backend.middleware :refer [standard-middleware]]
            [schema.core :as s]
            [goog.string :as gstring]
            [taoensso.timbre :as timbre])
  (:require-macros [cljs.core.match :refer [match]]))

(defn format [fmt & args]
  (apply gstring/format fmt args))

(def notification-msgs
  {:annotation
   {:in   {:ok {:simple (fn ([a] "Stored annotation for token [%d]")
                          ([a b] "Stored span annotation for range [%d-%d]"))
                :mult "Stored %d annotations!"}
           :error {:simple "Couldn't store annotation with id %d. Reason: [%s]"
                   :mult   "Couldn't store %d annotations! Reason: [%s]"}}}
   :info  "%s says: %s"
   :signup "Hooray! %s has joined the team!"
   :login "%s is ready for science"
   :logout "%s is leaving us..."
   :new-project "You've been added to project [%s] by user [%s]"})

(defn get-msg [path & args]
  (let [fmt (get-in notification-msgs path)]
    (cond (fn? fmt)     (apply format (apply fmt args) args)
          (string? fmt) (apply format fmt args))))

(defmulti incoming-annotation (fn [{:keys [ann-map] :as data}] (type ann-map)))
(defmethod incoming-annotation
  cljs.core/PersistentArrayMap
  [{hit-id :hit-id {span :span :as ann} :ann-map}]
  (re-frame/dispatch [:notify {:message (get-msg [:annotation :in :ok :simple] span)}])
  (re-frame/dispatch [:add-annotation {:hit-id hit-id :ann-map ann}]))
(defmethod incoming-annotation
  cljs.core/PersistentVector
  [{:keys [hit-id ann-map]}]
  (let [message (get-msg [:annotation :in :ok :mult] (count ann-map))]
    (re-frame/dispatch [:notify {:message message}])
    (doseq [[ann-map hit-id] (map vector ann-map hit-id)]
      (re-frame/dispatch [:add-annotation {:hit-id hit-id :ann-map ann-map}]))))

(defmulti incoming-annotation-error (fn [{:keys [scope]}] (type scope)))
(defmethod incoming-annotation-error
  js/Number
  [{scope :scope reason :reason}]
  (re-frame/dispatch
   [:notify {:message (get-msg [:annotation :in :error :simple] scope reason)}]))
(defmethod incoming-annotation-error
  cljs.core/PersistentVector
  [{:keys [scope reason e username]}]
  (re-frame/dispatch
   [:notify {:message (get-msg [:annotation :in :error :mult] (count scope) e)}]))
(defmethod incoming-annotation-error
  cljs.core/PersistentArrayMap
  [{:keys [{B :B O :O} reason e username]}]
  (re-frame/dispatch
   [:notify {:message (get-msg [:annotation :in :error :mult]) (count (range B (inc O))) e}]))

(re-frame/register-handler
 :ws
 standard-middleware
 (fn [db [_ dir {:keys [type status data payload-id] :as payload}]]
   (match [dir type status]
     ;; annotation routes
     [:in :annotation :ok] (do (incoming-annotation data)
                               (update-in db [:session :throbbing?] dissoc payload-id))
     
     [:in :annotation :error] (do (incoming-annotation-error data)
                                  (update-in db [:session :throbbing?] dissoc payload-id))
     
     [:out :annotation _] (do (send-ws payload)
                              (assoc-in db [:session :throbbing? payload-id] true))

     ;; notify routes
     ;; trigger notification
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
                                      message (get-msg [:new-project] creator project-name)]
                                  (re-frame/dispatch [:add-project project])
                                  (re-frame/dispatch [:notify {:message message}])
                                  db))))
