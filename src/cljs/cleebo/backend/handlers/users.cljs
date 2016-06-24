(ns cleebo.backend.handlers.users
  (:require [re-frame.core :as re-frame]
            [taoensso.timbre :as timbre]
            [cleebo.app-utils :refer [update-coll]]))

(defn avatar-error-handler [& args]
  (re-frame/dispatch [:notify {:message "Couldn't update avatar"}]))

(re-frame/register-handler              ;set client user info
 :set-user
 standard-middleware
 (fn [db [path value]]
   (assoc-in db (into [:me] path) value)))

(defn update-users
  [db [name path value]]
  (let [pred (fn [{username :username}] (= username name))]
     (update-in db [:users :rest] update-coll pred assoc-in (into [:user] path) value)))

(re-frame/register-handler              ;set other users info
 :set-users
 standard-middleware
 (fn [db [_ [name path value]]]
   (update-users db [name path value])))

(re-frame/register-handler              ;add user to client (after new signup)
 :add-user
 standard-middleware
 (fn [db [_ user]]
   (let [user (update-in user [:roles] (partial apply hash-set))]
     (update-in db [:users :rest] into [{:username (:username user) :user user}]))))

(re-frame/register-handler
 :regenerate-avatar
 (fn [db _]
   (POST "settings"
         {:params {:route :new-avatar}
          :handler #(re-frame/dispatch [:set-user [:avatar] %])
          :error-handler avatar-error-handler})
   db))

(re-frame/register-handler
 :new-user-avatar
 (fn [db [_ {:keys [username avatar]}]]
   (update-users db [username [:avatar] avatar])))

(re-frame/register-handler
 :update-user-active
 standard-middleware
 (fn [db [_ username status]]
   (update-users db [username [:active] status])))
