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
 :add-default-opts-map
 standard-middleware
 (fn [db [_ key & [default-opts]]]
   (update-in db [:settings :query key] conj (or default-opts (default-opts-map key)))))

(re-frame/register-handler              ;key is one of (:sort-opts, :filter-opts)
 :remove-opts-map
 standard-middleware
 (fn [db [_ key]] (update-in db [:settings :query key] pop)))

(re-frame/register-handler              ;set session data related to active project
 :set-project-settings
 standard-middleware
 (fn [db [_ path value]]
   (let [active-project (get-in db [:session :active-project])]
     ;; TODO: this should update session settings {:settings}
     (assoc-in db (into [:projects active-project :settings] path) value))))

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

(re-frame/register-handler
 :submit-settings
 (fn [db [_ update-map]]
   (POST "settings/save-settings"
         {:params {:update-map update-map}
          :handler #(re-frame/dispatch [:notify {:message "Successfully saved settings"}])
          :error-handler #(re-frame/dispatch
                           [:notify {:message "Error while saving settings" :status :error}])})
   db))

