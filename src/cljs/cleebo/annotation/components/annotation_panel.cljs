(ns cleebo.annotation.components.annotation-panel
  (:require [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [cleebo.components :refer [error-panel]]
            [cleebo.annotation.components.annotation-component
             :refer [annotation-component]]
            [cleebo.annotation.components.toolbar :refer [toolbar]]))

(defn annotation-panel []
  (let [marked-hits (re-frame/subscribe [:marked-hits {:has-marked? false}])
        open-hits (reagent/atom #{})]
    (fn []
      [annotation-component marked-hits open-hits])))
