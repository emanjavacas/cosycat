(ns cleebo.backend.handlers.projects
  (:require [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [ajax.core :refer [POST]]
            [cleebo.backend.middleware :refer [standard-middleware]]
            [cleebo.schemas.project-schemas :refer [project-schema update-schema]]
            [taoensso.timbre :as timbre]))



(re-frame/register-handler
 :add-project
 standard-middleware
 (fn [db [_ project]]
   (update-in db [:session :user-info :projects] conj project)))

(re-frame/register-handler
 :set-active-project
 standard-middleware
 (fn [db [_ project-name]]
   (let [projects (get-in db [:session :user-info :projects])
         project  (some #(= project-name (:name %)) projects)
         active-project {:name project-name
                         :filter-user-anns (into #{} (map :username (:users project)))}]
     (assert project)
     (assoc-in db [:session :active-project] active-project))))

(re-frame/register-handler
 :reset-active-project
 standard-middleware
 (fn [db _]
   (update-in db [:session] dissoc :active-project)))

(defn new-project-handler [project]
  (re-frame/dispatch [:add-project project]))

(defn new-project-error-handler [data]
  (timbre/debug data)
  (re-frame/dispatch
   [:notify {:message "Couldn't create project" :status :error}]))

(defn users-by-name [db & [usernames]]
  (filter #(some #{(:username %)} usernames) (get-in db [:session :users])))

(re-frame/register-handler
 :new-project
 standard-middleware
 (fn [db [_ {:keys [name description usernames] :as project}]]
   (let [users (if usernames (users-by-name db usernames) [])]
     (POST "/project"
           {:params {:route :new-project
                     :name name
                     :description description
                     :users (map #(select-keys % [:username :role]) users)
                     :csrf js/csrf}
            :handler new-project-handler
            :error-handler new-project-error-handler}))
   db))

(re-frame/register-handler
 :filter-anns-by-user
 standard-middleware
 (fn [db [_ username flag]]
   (let [action (if flag conj disj)]
     (update-in db [:session :active-project :filter-user-anns] action username))))

