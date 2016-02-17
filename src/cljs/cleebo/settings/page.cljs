(ns cleebo.settings.page
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]))

(defn settings-panel []
  [:div.container-fluid
   [:div.row [:h3 [:span.text-muted "Settings"]]]
   [:div.row [:hr]]])
