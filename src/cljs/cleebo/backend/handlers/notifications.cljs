(ns cleebo.backend.handlers.notifications
  (:require [re-frame.core :as re-frame]
            [cleebo.backend.subs :refer [get-user]]
            [cleebo.backend.middleware
             :refer [standard-middleware no-debug-middleware]]
            [cleebo.utils :refer [time-id]]))

(re-frame/register-handler
 :add-notification
 standard-middleware
 (fn [db [_ {:keys [data id] :as notification}]]
   (->> (assoc-in notification [:data :date] (js/Date.))
        (assoc-in db [:session :notifications id]))))

(re-frame/register-handler
 :drop-notification
 standard-middleware
 (fn [db [_ id]]
   (update-in db [:session :notifications] dissoc id)))

;;; todo; drop based on btns
(re-frame/register-handler
 :notify
 (fn [db [_ {:keys [message by] :as data}]]
   (let [id (time-id)
         delay (get-in db [:settings :notifications :delay])
         href (or (get-in (get-user db by) [:avatar :href]) "img/avatars/server.png")
         notification-payload {:data (assoc data :by {:href href}) :id id}]
     (js/setTimeout #(re-frame/dispatch [:drop-notification id]) delay)
     (re-frame/dispatch [:add-notification notification-payload]))
   db))
