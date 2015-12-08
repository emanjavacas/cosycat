(ns cleebo.core
    (:require [reagent.core :as reagent]
              [re-frame.core :as re-frame]
              [cleebo.handlers]
              [cleebo.subs]
              [cleebo.routes :as routes]
              [cleebo.views :as views]))

(defn mount-root []
  (reagent/render [views/main-panel]
                  (.getElementById js/document "app")))

(defn ^:export init [] 
  (routes/app-routes)
  (re-frame/dispatch-sync [:initialize-db])
  (mount-root))
