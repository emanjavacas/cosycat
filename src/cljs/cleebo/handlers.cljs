(ns cleebo.handlers
    (:require [re-frame.core :as re-frame]
              [cleebo.db :as db]))

(re-frame/register-handler
 :initialize-db
 (fn  [_ _]
   db/default-db))

(re-frame/register-handler
 :set-active-panel
 (fn [db [_ active-panel]]
   (assoc db :active-panel active-panel)))

(re-frame/register-handler
 :set-name
 (fn [db name]
   (assoc db :name name)))

(re-frame/register-handler
 :set-user
 (fn [db user]
   (assoc db :user user)))

(re-frame/register-handler
 :set-results
 (fn [db results]
   (assoc db :results results)))
