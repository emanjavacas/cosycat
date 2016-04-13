(ns cleebo.routes
    (:require-macros [secretary.core :refer [defroute]])
    (:import goog.History)
    (:require [secretary.core :as secretary]
              [goog.events :as events]
              [goog.history.EventType :as EventType]
              [re-frame.core :as re-frame]))

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
         (.scrollTo js/window 0 0)
         (secretary/dispatch! token))))
    (.setEnabled true)))

(defn app-routes []
  (secretary/set-config! :prefix "#")
  (defroute "/" []
    (re-frame/dispatch [:set-active-panel :front-panel]))
  (defroute "/query" []
    (re-frame/dispatch [:set-active-panel :query-panel]))
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

