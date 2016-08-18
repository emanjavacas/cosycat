(ns cleebo.backend.handlers.projects
  (:require [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [ajax.core :refer [POST]]
            [cleebo.app-utils :refer [pending-users]]
            [cleebo.routes :refer [nav!]]
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

(re-frame/register-handler              ;add project to client-db
 :add-project
 standard-middleware
 (fn [db [_ project]]
   (update db :projects merge (normalize-projects [project] (:me db)))))

(re-frame/register-handler              ;remove project from client-db
 :remove-project
 standard-middleware
 (fn [db [_ project-name]]
   (update db :projects dissoc project-name)))

(defn error-handler [{{:keys [message data]} :response}]
  (re-frame/dispatch [:notify {:message message :meta data :status :error}]))

(defn new-project-handler [{project-name :name :as project}]
  (re-frame/dispatch [:add-project project])
  (nav! (str "/project/" project-name)))

(re-frame/register-handler
 :new-project
 standard-middleware
 (fn [db [_ {:keys [name description users] :as project}]]
   (POST "/project/new"
         {:params {:project-name name
                   :description description
                   :users users}
          :handler new-project-handler
          :error-handler error-handler})
   db))

(re-frame/register-handler              ;add project update to client-db
 :add-project-update
 standard-middleware
 (fn [db [_ [{:keys [payload project-name]}]]]
   (update-in db [:projects project-name :updates] conj payload)))

(re-frame/register-handler
 :project-update
 standard-middleware
 (fn [db [_ {:keys [payload project-name]}]]
   (POST "/project/update"
         {:params {:project-name project-name :payload payload}
          :handler #(re-frame/dispatch [:add-project-update %])
          :error-handler error-handler})
   db))

(re-frame/register-handler              ;add user to project in client-db
 :add-project-user
 standard-middleware
 (fn [db [_ [{:keys [user project-name]}]]]
   (update-in db [:projects project-name :users] conj user)))

(re-frame/register-handler
 :project-add-user
 standard-middleware
 (fn [db [_ {:keys [user project-name]}]]
   (POST "/project/add-user"
         {:params {:user user :project-name project-name}
          :handler #(re-frame/dispatch [:add-project-user %])
          :error-handler error-handler})
   db))

(re-frame/register-handler              ;remove user from project in client-db
 :remove-project-user
 standard-middleware
 (fn [db [_ [{:keys [user project-name]}]]]
   (update-in
    db [:projects project-name :users]
    (fn [users] (vec (remove #(= (:username %) (:username user)) users))))))

(re-frame/register-handler
 :project-remove-user
 (fn [db [_ {:keys [project-name]}]]
   (POST "/project/remove-user"
         {:params {:project-name project-name}
          :handler #(re-frame/dispatch [:notify "Goodbye from project " project-name])
          :error-handler error-handler})
   db))

(defn remove-project-handler [{project-name :name :as project}]
  (fn [payload]
    (if (empty? payload)
      (do (re-frame/dispatch [:remove-project project-name])
          (re-frame/dispatch
           [:notify {:message (str "Project " project-name " was successfully deleted")}])
          (nav! "/"))
      (let [updated-project (update-in project [:updates] conj payload)
            {:keys [pending]} (pending-users updated-project)] ;still users
        (re-frame/dispatch
         [:notify {:message (str (count pending) " users pending to remove project")}])
        (re-frame/dispatch [:add-project-update {:payload payload :project-name project-name}])))))

(re-frame/register-handler
 :project-remove
 (fn [db [_ {:keys [project-name]}]]
   (POST "/project/remove-project"
         {:params {:project-name project-name}
          :handler (remove-project-handler (get-in db [:projects project-name]))
          :error-handler error-handler})
   db))
