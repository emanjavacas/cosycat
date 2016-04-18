(ns cleebo.backend.handlers.projects
  (:require [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [ajax.core :refer [POST]]
            [cleebo.backend.middleware :refer [standard-middleware]]
            [cleebo.schemas.project-schemas :refer [project-schema update-schema]]
            [taoensso.timbre :as timbre]))

(defn new-project-handler [project]
  (re-frame/dispatch [:add-project project]))

(defn new-project-error-handler [data]
  (timbre/debug data)
  (re-frame/dispatch
   [:notify {:message "Couldn't create project" :status :error}]))

(re-frame/register-handler
 :add-project
 standard-middleware
 (fn [db [_ project]]
   (update-in db [:session :user-info :projects] conj project)))

(re-frame/register-handler
 :set-active-project
 standard-middleware
 (fn [db [_ project-name]]
   (let [projects (get-in db [:session :user-info :projects])]
     (assert (some #(= project-name (:name %)) projects))
     (assoc-in db [:session :active-project] project-name))))

(re-frame/register-handler
 :reset-active-project
 standard-middleware
 (fn [db _]
   (update-in db [:session] dissoc :active-project)))

(re-frame/register-handler
 :select-next-project
 standard-middleware
 (fn [db _]
   (let [active-project (get-in db [:session :active-project])
         projects (get-in db [:session :user-info :projects])
         next-projects (drop-while #(not= (:name %) active-project))]
     (assert (not (empty? projects)))
     (assoc-in db [:session :active-project] (if (= (count next-projects) 1)
                                               (first projects)
                                               (next next-projects))))))

(re-frame/register-handler
 :new-project
 standard-middleware
 (fn [db [_ {:keys [name description users] :as project}]]
   (POST "/project"
         {:params {:route :new-project
                   :name name
                   :description description
                   :users (or users [])
                   :csrf js/csrf}
          :handler new-project-handler
          :error-handler new-project-error-handler})
   db))

