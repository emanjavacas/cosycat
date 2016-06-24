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
     ;; TODO: this should send the updated settings to the db
     (update-in db [:projects] update-coll pred assoc-in (into [:settings] path)) value)))
