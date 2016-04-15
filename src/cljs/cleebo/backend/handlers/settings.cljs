(ns cleebo.backend.handlers.settings
  (:require [re-frame.core :as re-frame]
            [cleebo.backend.middleware :refer [standard-middleware]]))

(re-frame/register-handler
 :update-notification
 standard-middleware
 (fn [db [_ path f]]
   (let [notification-settings (get-in db [:settings :notifications])]
     (assoc-in db [:settings :notifications]
               (update-in notification-settings path f)))))

(re-frame/register-handler
 :set-snippet-size
 (fn [db [_ snippet-size]]
   (assoc-in db [:settings :snippets :snippet-size] snippet-size)))

(re-frame/register-handler
 :set-snippet-delta
 (fn [db [_ snippet-delta]]
   (assoc-in db [:settings :snippets :snippet-delta] snippet-delta)))
