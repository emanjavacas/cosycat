(ns cleebo.handlers
    (:require [taoensso.timbre :as timbre]
              [re-frame.core :as re-frame]
              [cleebo.db :as db]))

(re-frame/register-handler
 :initialize-db
 (fn  [_ _]
   db/default-db))

(re-frame/register-handler
 :set-active-panel
 (fn [db [_ active-panel]]
   (assoc db :active-panel active-panel)))

(re-frame/register-handler
 :start-throbbing
 (fn [db [_ panel]]
   (assoc-in db [:throbbing? panel] true)))

(re-frame/register-handler
 :stop-throbbing
 (fn [db [_ panel]]
   (assoc-in db [:throbbing? panel] false)))

(re-frame/register-handler
 :set-name
 (fn [db [_ name]]
   (assoc db :name name)))

(re-frame/register-handler
 :set-session
 (fn [db [_ path value]]
   (let [session (:session db)]
     (assoc db :session (assoc-in session path value)))))

(defn handle-ws [db {:keys [type msg]}]
  (timbre/debug "Handling " {:type type :msg msg})
  (case type
    :msgs (update db type conj [msg])))

(re-frame/register-handler
 :ws-in
 (fn [db [_ data]]
   (let [{:keys [status type msg]} data]
     (cond
       (= status :error) (do (timbre/debug msg) db)
       (= status :ok)    (handle-ws db {:type type :msg msg})
       :else             (do (timbre/debug "Unknown status: " status) db)))))

(re-frame/register-handler
 :set-query-results
 (fn [db [_ & [{:keys [results query-size query-str status from to] :as data}]]]
   (update-in db [:session :query-results] merge data)))
