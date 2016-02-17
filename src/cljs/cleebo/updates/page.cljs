(ns cleebo.updates.page
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]))

(defn updates-panel []
  [:div.container-fluid
   [:div.row [:h3 [:span.text-muted "Updates (what's new in your feed)"]]]
   [:div.row [:hr]]])
