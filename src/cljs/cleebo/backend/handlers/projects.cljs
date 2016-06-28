(ns cleebo.backend.handlers.projects
  (:require [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [ajax.core :refer [POST]]
            [cleebo.backend.middleware :refer [standard-middleware check-project-exists]]
            [cleebo.backend.db :refer [default-project-session default-project-history]]
            [cleebo.schemas.project-schemas :refer [project-schema update-schema]]
            [taoensso.timbre :as timbre]))

(re-frame/register-handler
 :set-active-project
 (conj standard-middleware check-project-exists)
 (fn [db [_ {:keys [project-name]}]]
   (assoc-in db [:session :active-project] project-name)))

(defn normalize-projects [projects]
  (reduce (fn [acc {:keys [name] :as project}]
            (let [project {:project project :session default-project-session}]
              (assoc acc name project)))
          {}
          projects))

(re-frame/register-handler
 :add-project
 standard-middleware
 (fn [db [_ project]]
   (update db [:projects] merge (normalize-projects [project]))))

(defn new-project-handler [project]   ;should navigate to project
  (re-frame/dispatch [:add-project project])
  (re-frame/dispatch [:notify {:message "Succesfully created project"}]))

(defn new-project-error-handler [{:keys [message data]}]
  (re-frame/dispatch
   [:notify {:message (str "Couldn't create project: [" data "]" ) :status :error}]))

(re-frame/register-handler
 :new-project
 standard-middleware
 (fn [db [_ {:keys [name description users] :as project}]]
   (POST "/project"
         {:params {:route :new-project
                   :name name
                   :description description
                   :users users}
          :handler new-project-handler
          :error-handler new-project-error-handler})
   db))

(re-frame/register-handler
 :update-filtered-users
 standard-middleware
 (fn [db [_ username flag]]
   (let [active-project (:active-project db)
         action (if flag conj disj)]
     (update-in db [:projects active-project :session :filtered-users] action username))))
