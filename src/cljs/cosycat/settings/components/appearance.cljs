(ns cosycat.settings.components.appearance
  (:require [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [react-bootstrap.components :as bs]
            [cosycat.components :refer [dropdown-select]]
            [cosycat.settings.components.shared-components :refer [row-component]]
            [taoensso.timbre :as timbre]))

(def help-map
  {:avatar "Click to generate a new avatar with random seed"})

(defn on-mouse-over [target text-atom] (fn [e] (reset! text-atom (get help-map target))))

(defn appearance-controller []
  (let [help-text (reagent/atom "")]
    (fn []
      [row-component
       :label "Avatar"
       :controllers [bs/button
                     {:onClick #(re-frame/dispatch [:regenerate-avatar])
                      :on-mouse-over (on-mouse-over :avatar help-text)}
                     "Get new avatar"]
       :help-text help-text])))

(defn appearance-settings []
  [:div.container-fluid
   [appearance-controller]])
