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

(re-frame/register-handler
 :set-results
 (fn [db results]
   (assoc db :results results)))

(defn handle-ws [db {:keys [type msg]}]
  (assoc-in db type msg))

;;; mock
(re-frame/register-handler
 :ws-in
 (fn [db data]
   (let [{:keys [status type msg]} data]
     (cond
       (= :error status) (do (timbre/debug msg) db)
       (= :ok    status) (handle-ws db {:type type :msg msg})
       :else (do (timbre/debug "Unknown status: " data) db)))))

(re-frame/register-handler
 :remove-last
 (fn [db _]
   (update db :input-msg #(vec (drop-last %)))))
