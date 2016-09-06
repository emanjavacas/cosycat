(ns cosycat.backend.handlers.users
  (:require [re-frame.core :as re-frame]
            [taoensso.timbre :as timbre]
            [cosycat.backend.middleware :refer [standard-middleware]]
            [cosycat.app-utils :refer [update-coll]]))

(re-frame/register-handler              ;set client user info
 :set-user
 standard-middleware
 (fn [db [_ path value]]
   (assoc-in db (into [:me] path) value)))

(defn update-users
  [db [name path value]]
  (let [pred (fn [{username :username}] (= username name))]
     (update db :users update-coll pred assoc-in (into [:user] path) value)))

(re-frame/register-handler              ;set other users info
 :set-users
 standard-middleware
 (fn [db [_ name path value]]
   (update-users db [name path value])))

(re-frame/register-handler              ;add user to client (after new signup)
 :add-user
 standard-middleware
 (fn [db [_ user]]
   (let [user (update-in user [:roles] (partial apply hash-set))]
     (update-in db [:users] into [{:username (:username user) :user user}]))))

(re-frame/register-handler
 :new-user-avatar
 (fn [db [_ {:keys [username avatar]}]]
   (update-users db [username [:avatar] avatar])))

(re-frame/register-handler
 :update-user-active
 standard-middleware
 (fn [db [_ username status]]
   (update-users db [username [:active] status])))
