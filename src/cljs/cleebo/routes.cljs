(ns cleebo.routes
  (:require [secretary.core :as secretary]
            [goog.events :as events]
            [goog.history.EventType :as EventType]
            [re-frame.core :as re-frame])
  (:import goog.History)
  (:require-macros [secretary.core :refer [defroute]]))

(defonce history (History.))

(defn nav! [token]
  (.setToken history token))

(defn hook-browser-navigation! []
  (doto history
    (events/listen
     EventType/NAVIGATE
     (fn [event]
       (let [token (.-token event)]
         (.log js/console "Navigating to " token)
         (secretary/dispatch! token))))
    (.setEnabled true)))

(defn app-routes []
  (secretary/set-config! :prefix "#")
  (defroute "/" []
    (re-frame/dispatch [:reset-active-project])
    (re-frame/dispatch [:set-active-panel :front-panel]))
  (defroute "/project/:project-name" {project-name :project-name}
    (re-frame/dispatch [:set-active-panel :query-panel])
    (re-frame/dispatch [:set-active-project {:project-name project-name}]))
  (defroute "/settings" []
    (re-frame/dispatch [:set-active-panel :settings-panel]))
  (defroute "/debug" []
    (re-frame/dispatch [:set-active-panel :debug-panel]))
  (defroute "/updates" []
    (re-frame/dispatch [:set-active-panel :updates-panel]))
  (defroute "/annotation" []
    (re-frame/dispatch [:set-active-panel :annotation-panel]))
  (defroute "/exit" []
    (.assign js/location "/logout"))
  (hook-browser-navigation!))

