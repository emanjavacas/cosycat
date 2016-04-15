(ns cleebo.backend.handlers.projects
  (:require [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [ajax.core :refer [POST]]
            [cleebo.backend.middleware :refer [standard-middleware]]
            [taoensso.timbre :as timbre]))

(defn new-project-handler [project]
  (re-frame/dispatch
   [:update-session [:user-info :projects] conj project]))

(defn new-project-error-handler []
  (re-frame/dispatch [:notify {:message "Couldn't create project" :status :error}]))

(re-frame/register-handler
 :new-project
 standard-middleware
 (fn [db [_ & {:keys [name description users]}]]
   (POST "project"
         {:params {:route :new-project
                   :name name
                   :description description
                   :users (or users [])}
          :handler new-project-handler
          :error-handler new-project-error-handler})))

