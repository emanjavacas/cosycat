(ns cleebo.backend.handlers.projects
  (:require [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [ajax.core :refer [POST]]
            [cleebo.backend.middleware :refer [standard-middleware]]
            [taoensso.timbre :as timbre]))

(defn new-project-handler [project]
  (re-frame/dispatch [:add-project project]))

(defn new-project-error-handler []
  (re-frame/dispatch
   [:notify {:message "Couldn't create project" :status :error}]))

(re-frame/register-handler
 :add-project
 standard-middleware
 (fn [db [_ project]]
   (update-in db [:session :user-info :projects] conj project)))

(re-frame/register-handler
 :new-project
 standard-middleware
 (fn [db [_ {:keys [name description users] :as project}]]
   (timbre/debug "POST" project)
   (POST "/project"
         {:params {:route :new-project
                   :name name
                   :description description
                   :users (or users [])
                   :csrf js/csrf}
          :handler new-project-handler
          :error-handler new-project-error-handler})
   db))

