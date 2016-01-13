(ns cleebo.pages.settings
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [re-com.core :as re-com]))

(defn settings-panel []
  [re-com/v-box
   :children
   [[:h4 [:span.text-muted "Settings"]]
    [re-com/line]]])
