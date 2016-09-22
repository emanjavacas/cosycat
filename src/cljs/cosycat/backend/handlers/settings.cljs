(ns cosycat.backend.handlers.settings
  (:require [re-frame.core :as re-frame]
            [cosycat.backend.middleware :refer [standard-middleware]]
            [cosycat.backend.db :refer [default-opts-map]]
            [ajax.core :refer [POST GET]]
            [taoensso.timbre :as timbre]))

(re-frame/register-handler              ;set global settings data to given path
 :set-settings
 standard-middleware
 (fn [db [_ path value]]
   (let [settings (get-in db [:settings])]
     (assoc-in db [:settings] (assoc-in settings path value)))))

(re-frame/register-handler
 :set-corpus
 standard-middleware
 (fn [db [_ corpus-name]]
   (re-frame/dispatch [:unset-query-results])
   (assoc-in db [:settings :query :corpus] corpus-name)))

(re-frame/register-handler              ;key is one of (:sort-opts, :filter-opts)
 :add-opts-map
 standard-middleware
 (fn [db [_ key & [opts]]]
   (update-in db [:settings :query key] conj (or opts (default-opts-map key)))))

(re-frame/register-handler              ;key is one of (:sort-opts, :filter-opts)
 :remove-opts-map
 standard-middleware
 (fn [db [_ key & {:keys [update-f] :or {update-f pop}}]]
   (update-in db [:settings :query key] update-f)))

(re-frame/register-handler              ;set session data related to active project
 :set-project-settings
 standard-middleware
 (fn [db [_ path value]]
   (let [active-project (get-in db [:session :active-project])]
     ;; TODO: this should update general settings {:settings}
     (-> db
         (assoc-in (into [:settings] path) value)
         (assoc-in (into [:projects active-project :settings] path) value)))))

(defn avatar-error-handler [& args]
  (re-frame/dispatch [:notify {:message "Couldn't update avatar"}]))

(re-frame/register-handler
 :regenerate-avatar
 (fn [db _]
   (POST "settings/new-avatar"
         {:params {}
          :handler #(re-frame/dispatch [:set-user [:avatar] %])
          :error-handler avatar-error-handler})
   db))

(defn submit-settings-error-handler []
  (re-frame/dispatch [:notify {:message "Couldn't save settings" :status :error}]))

(re-frame/register-handler
 :submit-settings
 (fn [db [_ update-map]]
   (POST "settings/save-settings"
         {:params {:update-map update-map}
          :handler #(re-frame/dispatch [:notify {:message "Successfully saved settings"}])
          :error-handler submit-settings-error-handler})
   db))

(defn submit-project-settings-error-handler []
  (re-frame/dispatch [:notify {:message "Couldn't save settings" :status :error}]))

(defn submit-project-settings-handler []
  (re-frame/dispatch [:notify {:message "Successfully saved project settings"}]))

(re-frame/register-handler
 :submit-project-settings
 (fn [db [_ update-map]]
   (let [active-project (get-in db [:session :active-project])]
     (POST "settings/save-project-settings"
           {:params {:update-map update-map :project active-project}
            :handler submit-project-settings-handler
            :error-handler submit-project-settings-error-handler})
     db)))



