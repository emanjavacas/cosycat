(ns cleebo.backend.handlers.settings
  (:require [re-frame.core :as re-frame]
            [cleebo.backend.middleware :refer [standard-middleware]]
            [ajax.core :refer [POST]]
            [taoensso.timbre :as timbre]))

(re-frame/register-handler              ;set global settings data to given path
 :set-settings
 standard-middleware
 (fn [db [_ path value]]
   (let [settings (get-in db [:session :settings])]
     (assoc db [:session :settings] (assoc-in settings path value)))))

(re-frame/register-handler              ;set session data related to active project
 :set-project-settings
 standard-middleware
 (fn [db [_ path value]]
   (let [active-project (get-in db [:session :active-project])
         pred (fn [{:keys [name]}] (= name active-project))]
     ;; TODO: this should also send the updated settings to the db.
     (assoc-in db (into [:projects active-project :settings] path) value))))

(defn avatar-error-handler [& args]
  (re-frame/dispatch [:notify {:message "Couldn't update avatar"}]))

(re-frame/register-handler
 :regenerate-avatar
 (fn [db _]
   (POST "settings"
         {:params {:route :new-avatar}
          :handler #(re-frame/dispatch [:set-user [:avatar] %])
          :error-handler avatar-error-handler})
   db))
