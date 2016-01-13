(ns cleebo.routes
    (:require-macros [secretary.core :refer [defroute]])
    (:import goog.History)
    (:require [secretary.core :as secretary]
              [goog.events :as events]
              [goog.history.EventType :as EventType]
              [re-frame.core :as re-frame]))

(defn hook-browser-navigation! []
  (doto (History.)
    (events/listen
     EventType/NAVIGATE
     (fn [event]
       (secretary/dispatch! (.-token event))))
    (.setEnabled true)))

(defn app-routes []
  (secretary/set-config! :prefix "#")
  (defroute "/" []
    (re-frame/dispatch [:set-active-panel :query-panel]))
  (defroute "/query" []
    (re-frame/dispatch [:set-active-panel :query-panel]))
  (defroute "/settings" []
    (re-frame/dispatch [:set-active-panel :settings-panel]))
  (defroute "/exit" []
    (.assign js/location "/logout"))
  (hook-browser-navigation!))

