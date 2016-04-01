(ns cleebo.backend.handlers.components
  (:require [re-frame.core :as re-frame]
            [cleebo.backend.middleware :refer [standard-middleware no-debug-middleware]]
            [cleebo.utils :refer [deep-merge time-id]]))

(re-frame/register-handler
 :open-modal
 standard-middleware
 (fn [db [_ modal & [data]]]
   (if-not data
     (assoc-in db [:modals modal] true)     
     (update-in db [:modals modal] deep-merge data))))

(re-frame/register-handler
 :close-modal
 (fn [db [_ modal]]
   (assoc-in db [:modals modal] false)))

(re-frame/register-handler
 :set-active-panel
 (fn [db [_ active-panel]]
   (assoc db :active-panel active-panel)))

(re-frame/register-handler
 :add-notification
 standard-middleware
 (fn [db [_ {:keys [data id] :as notification}]]
   (assoc-in db [:notifications id] (assoc-in notification [:data :date] (js/Date.)))))

(re-frame/register-handler
 :drop-notification
 standard-middleware
 (fn [db [_ id]]
   (update-in db [:notifications] dissoc id)))

(re-frame/register-handler
 :notify
 (fn [db [_ {:keys [message by status] :as data}]]
   (let [id (time-id)
         delay (get-in db [:settings :delay])]
     (js/setTimeout #(re-frame/dispatch [:drop-notification id]) delay)
     (re-frame/dispatch [:add-notification {:data data :id id}]))
   db))

(re-frame/register-handler
 :start-throbbing
 standard-middleware
 (fn [db [_ panel]]
   (assoc-in db [:throbbing? panel] true)))

(re-frame/register-handler
 :stop-throbbing
 standard-middleware
 (fn [db [_ panel]]
   (assoc-in db [:throbbing? panel] false)))
