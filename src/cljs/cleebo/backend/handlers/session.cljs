(ns cleebo.backend.handlers.session
  (:require [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [ajax.core :refer [GET]]
            [cleebo.backend.middleware :refer [standard-middleware]]
            [taoensso.timbre :as timbre]))

(re-frame/register-handler
 :set-session
 standard-middleware
 (fn [db [_ path value]]
   (let [session (:session db)]
     (assoc db :session (assoc-in session path value)))))

(re-frame/register-handler
 :update-session
 standard-middleware
 (fn [db [_ path f & args]]
   (update-in db (into [:session] path) f args)))

(defn session-handler
  [{{username :username [roles] :roles projects :projects :as user-info} :user-info
    users :users :as payload}]
  (let [user-info (assoc user-info :roles (hash-set roles))
        users (map #(update-in % [:roles] (partial apply hash-set)) users)]
    (timbre/debug payload users)
    (re-frame/dispatch [:set-session [:user-info] user-info])
    (re-frame/dispatch [:set-session [:users] users])))

(defn session-error-handler [data]
  (timbre/debug data))

(re-frame/register-handler
 :fetch-user-info
 standard-middleware
 (fn [db _]
   (GET "/session"
        {:handler session-handler
         :error-handler session-error-handler})
   db))
