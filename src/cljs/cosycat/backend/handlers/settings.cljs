(ns cosycat.backend.handlers.settings
  (:require [re-frame.core :as re-frame]
            [cosycat.app-utils :refer [deep-merge]]
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
 :update-settings
 standard-middleware
 (fn [db [_ settings]]
   (update db :settings deep-merge settings)))

(re-frame/register-handler
 :set-corpus
 standard-middleware
 (fn [db [_ corpus-name]]
   (re-frame/dispatch [:unset-query-results]) ;get rid of results in current query
   (re-frame/dispatch [:reset-project-settings :init {:query {:corpus corpus-name}}]) ;reset sort/filter etc..
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
     (-> db
         (assoc-in (into [:settings] path) value)
         (assoc-in (into [:projects active-project :settings] path) value)))))

(re-frame/register-handler
 :update-project-settings
 standard-middleware
 (fn [db [_ project-name settings]]
   (update-in db [:projects project-name :settings] deep-merge settings)))

(defn submit-settings-handler [settings]
  (re-frame/dispatch [:update-settings settings])
  (re-frame/dispatch [:notify {:message "Successfully saved settings"}]))

(defn submit-settings-error-handler []
  (re-frame/dispatch [:notify {:message "Couldn't save settings" :status :error}]))

(re-frame/register-handler
 :submit-settings
 (fn [db [_ update-map]]
   (POST "settings/save-settings"
         {:params {:update-map update-map}
          :handler submit-settings-handler
          :error-handler submit-settings-error-handler})
   db))

(defn submit-project-settings-error-handler []
  (re-frame/dispatch [:notify {:message "Couldn't save settings" :status :error}]))

(defn submit-project-settings-handler [project-name]
  (fn [settings]
    (re-frame/dispatch [:update-project-settings project-name settings])
    (re-frame/dispatch [:notify {:message "Successfully saved project settings"}])))

(re-frame/register-handler
 :submit-project-settings
 (fn [db [_ update-map]]
   (let [active-project (get-in db [:session :active-project])]
     (POST "settings/save-project-settings"
           {:params {:update-map update-map :project active-project}
            :handler (submit-project-settings-handler active-project)
            :error-handler submit-project-settings-error-handler})
     db)))
