(ns cleebo.backend.handlers.settings
  (:require [re-frame.core :as re-frame]
            [cleebo.backend.middleware :refer [standard-middleware]]
            [ajax.core :refer [POST]]
            [taoensso.timbre :as timbre]))

(re-frame/register-handler
 :update-notification
 standard-middleware
 (fn [db [_ path f]]
   (let [notification-settings (get-in db [:settings :notifications])]
     (assoc-in db [:settings :notifications]
               (update-in notification-settings path f)))))

(re-frame/register-handler
 :set-snippet-size
 standard-middleware
 (fn [db [_ snippet-size]]
   (assoc-in db [:settings :snippets :snippet-size] snippet-size)))

(re-frame/register-handler
 :set-snippet-delta
 standard-middleware
 (fn [db [_ snippet-delta]]
   (assoc-in db [:settings :snippets :snippet-delta] snippet-delta)))

(re-frame/register-handler
 :regenerate-avatar
 (fn [db _]
   (POST "settings"
         {:params {:route :new-avatar}
          :handler #(re-frame/dispatch [:set-session [:user-info :avatar] %])
          :error-handler #(re-frame/dispatch [:notify {:message "Couldn't update avatar"}])})
   db))

(re-frame/register-handler
 :new-user-avatar
 (fn [db [_ {:keys [username avatar]}]]
   (update-in
    db [:session :users]
    (fn [users] (map (fn [user]
                       (timbre/debug user)
                       (if (= username (:username user))
                         (assoc user :avatar avatar)
                         user))
                     users)))))
