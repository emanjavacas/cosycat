(ns cleebo.backend.handlers.session
  (:require [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [ajax.core :refer [GET]]
            [cleebo.backend.db :refer [default-db]]
            [cleebo.backend.middleware :refer [standard-middleware]]
            [cleebo.app-utils :refer [default-project-name update-coll]]
            [cleebo.utils :refer [format]]
            [taoensso.timbre :as timbre]))

(re-frame/register-handler              ;set session data to given path
 :set-session
 standard-middleware
 (fn [db [_ path value]]
   (let [session (:session db)]
     (assoc db :session (assoc-in session path value)))))

(re-frame/register-handler              ;set session data related to active project
 :set-project-session
 standard-middleware
 (fn [db [_ path value]]
   (let [active-project (get-in db [:session :active-project])
         pred (fn [{:keys [name]}] (= name active-project))]
     (update-in db [:projects] update-coll pred assoc-in (into [:session] path)) value)))

(re-frame/register-handler
 :set-active-panel
 standard-middleware
 (fn [db [_ active-panel]]
   (assoc-in db [:session :active-panel] active-panel)))

(re-frame/register-handler
 :update-notification
 standard-middleware
 (fn [db [_ path f]]
   (let [notification-settings (get-in db [:settings :notifications])]
     (assoc-in db [:settings :notifications]
               (update-in notification-settings path f)))))

(re-frame/register-handler
 :initialize-db
 standard-middleware
 (fn [_ [_ {:keys [me users corpora projects] :as payload}]]
   (deep-merge payload )))              ;TODO

(defn session-handler
  [{me :me users :users projects :projects corpora :corpora :as payload}]
  (re-frame/dispatch [:initialize-db payload])
  (js/setTimeout #(re-frame/dispatch [:stop-throbbing :front-panel]) 2000))

(defn session-error-handler [data]
  (re-frame/dispatch [:stop-throbbing :front-panel])
  (re-frame/dispatch
   [:session-error
    {:error "initialisation error"
     :message "Couldn't load user session. Try refreshing the browser :-S"}]))

(re-frame/register-handler
 :init-session
 standard-middleware
 (fn [db _]
   (re-frame/dispatch [:start-throbbing :front-panel])
   (GET "/session"
        {:handler session-handler
         :error-handler session-error-handler})
   db))

(re-frame/register-handler              ;global error
 :session-error
 standard-middleware
 (fn [db [_ {:keys [error message] :as args}]]
   (-> db
       (assoc-in [:session :active-panel] :error-panel)
       (assoc-in [:session :session-error] args))))
