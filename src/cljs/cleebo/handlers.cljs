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
 :set-name
 (fn [db name]
   (assoc db :name name)))

(re-frame/register-handler
 :set-user
 (fn [db user]
   (assoc db :user user)))

(defn handle-ws [db {:keys [type msg]}]
  (timbre/debug "Handling " {:type type :msg msg})
  (update db type conj [msg]))

(re-frame/register-handler
 :ws-in
 (fn [db [_ data]]
   (let [{:keys [status type msg]} data]
     (cond
       (= status :error) (do (timbre/debug msg) db)
       (= status :ok)    (handle-ws db {:type type :msg msg})
       :else             (do (timbre/debug "Unknown status: " status) db)))))
