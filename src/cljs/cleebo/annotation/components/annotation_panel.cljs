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
      (if (zero? (count @marked-hits))
        [:div.container-fluid
         {:style {:width "100%" :padding "0 10px 0 10px" :margin-top "65px"}}
         [error-panel :status "No hits marked for annotation..."]]
        [:div.container-fluid
         {:style {:width "100%" :padding "0 10px 0 10px" :margin-top "65px"}}
         [:div.row
          [:div.col-lg-12 [annotation-component marked-hits open-hits]]]
         [toolbar marked-hits open-hits]]))))
