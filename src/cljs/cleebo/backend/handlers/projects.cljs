(ns cleebo.backend.handlers.projects
  (:require [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [ajax.core :refer [POST]]
            [cleebo.backend.middleware :refer [standard-middleware check-project-exists]]
            [cleebo.backend.db :refer [default-project-session default-project-history]]
            [cleebo.schemas.project-schemas :refer [project-schema update-schema]]
            [taoensso.timbre :as timbre]))

(defn normalize-projects
  "transforms server project schema to client project schema"
  [projects user]
  (reduce
   (fn [acc {:keys [name] :as project}]
     (let [{:keys [history settings]} (some #(when (= name (:name %)) %) (:projects user))]
       (assoc acc name (-> project
                           (assoc :session (default-project-session project))
                           (cond-> history (assoc :history history))
                           (cond-> settings (assoc :settings settings))))))
   {}
   projects))

(re-frame/register-handler
 :remove-active-project
 standard-middleware
 (fn [db _]
   (assoc-in db [:session :active-project] nil)))

(re-frame/register-handler
 :set-active-project
 (conj standard-middleware check-project-exists)
 (fn [db [_ {:keys [project-name]}]]
   (let [project-settings (get-in db [:projects project-name :settings] {})]
     (-> db
         (assoc-in [:session :active-project] project-name)
         (update-in [:session :settings] merge project-settings)))))

(re-frame/register-handler
 :add-project
 standard-middleware
 (fn [db [_ project]]
   (update db :projects merge (normalize-projects [project] (:me db)))))

(defn error-handler [{:keys [message data]}]
  (re-frame/dispatch [:notify {:message message :meta data :status :error}]))

(defn new-project-handler [project]
  (re-frame/dispatch [:add-project project])
  (re-frame/dispatch [:set-active-project {:project-name (:name project)}]))

(defn user->project-user [{:keys [username]}]
  {:username username :role "creator"})

(re-frame/register-handler
 :new-project
 standard-middleware
 (fn [db [_ {:keys [name description users] :as project}]]
   (POST "/project"
         {:params {:route :new-project
                   :name name
                   :description description
                   :users (conj users (user->project-user (:me db)))}
          :handler new-project-handler
          :error-handler error-handler})
   db))

(defn project-update-error-handler [{:keys [message data]}])

(re-frame/register-handler
 :add-project-update
 standard-middleware
 (fn [db [_ [{:keys [payload project]}]]]
   (update-in db [:projects project :updates] conj payload)))

(re-frame/register-handler
 :project-update
 standard-middleware
 (fn [db [_ {:keys [payload project]}]]
   (POST "/project"
         {:params {:route :project-update :project project :payload payload}
          :handler #(re-frame/dispatch [:add-project-update %])
          :error-handler #(timbre/info "Error while sending project update to server")})
   db))

(re-frame/register-handler
 :add-project-user
 standard-middleware
 (fn [db [_ [{:keys [user project]}]]]
   (update-in db [:projects project :users] conj user)))

(re-frame/register-handler
 :project-add-user
 standard-middleware
 (fn [db [_ {:keys [user project]}]]
   (POST "/project"
         {:params {:route :add-user :user user :project project}
          :handler #(re-frame/dispatch [:add-project-user %])
          :error-handler error-handler})
   db))

(re-frame/register-handler
 :remove-project-user
 standard-middleware
 (fn [db [_ [{:keys [user project]}]]]
   (update-in
    db [:projects project :users]
    (fn [users] (vec (remove #(= (:username %) (:username user)) users))))))

(re-frame/register-handler
 :project-remove-user
 (fn [db [_ {:keys [project]}]]
   (POST "/project"
         {:params {:route :remove-user :project project}
          :handler #(re-frame/dispatch [:notify "Goodbye from project " project])
          :error-handler error-handler})
   db))
