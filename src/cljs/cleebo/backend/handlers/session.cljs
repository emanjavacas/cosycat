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
   (update-in db (concat [:session] path) f args)))

(defn preprocess-user [user]
  (update-in user [:roles] (partial apply hash-set)))

(re-frame/register-handler
 :add-user
 standard-middleware
 (fn [db [_ user]]
   (update-in db [:session :users] conj (preprocess-user user))))

(re-frame/register-handler
 :user-status
 standard-middleware
 (fn [db [_ target-username status]]
   (assert (some #(= target-username (:username %)) (get-in db [:session :users])))
   (update-in
    db [:session :users]
    (fn [users]
      (map (fn [{:keys [username] :as user}]
             (if (= username target-username)
               (assoc user :active status)
               user)))
      users))))

(defn session-handler
  [{{username :username [roles] :roles projects :projects :as user-info} :user-info
    corpora :corpora users :users :as payload}]
  (let [user-info (assoc user-info :roles (hash-set roles))
        users (map preprocess-user users)]
    (re-frame/dispatch [:set-session [:user-info] user-info])
    (re-frame/dispatch [:set-session [:corpora] corpora])
    (re-frame/dispatch [:set-session [:query-opts :corpus] (first corpora)])
    (re-frame/dispatch [:set-session [:users] users])
    (js/setTimeout #(re-frame/dispatch [:stop-throbbing :front-panel]) 2000)))

(defn session-error-handler [data]
  (re-frame/dispatch [:stop-throbbing :front-panel])
  (timbre/debug data))

(re-frame/register-handler
 :fetch-user-info
 standard-middleware
 (fn [db _]
   (re-frame/dispatch [:start-throbbing :front-panel])
   (GET "/session"
        {:handler session-handler
         :error-handler session-error-handler})
   db))
