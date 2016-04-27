(ns cleebo.annotation.page
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [react-bootstrap.components :as bs]
            [cleebo.components :refer [error-panel]]
            [cleebo.annotation.components.annotation-component
             :refer [annotation-component]]
            [cleebo.annotation.components.toolbar :refer [toolbar]]))

(defn back-to-query-button []
  (let [active-project (re-frame/subscribe [:session :active-project :name])]
    (fn []
      [bs/button {:href (if @active-project (str "#/project/" @active-project) "#/")}
       [:span {:style {:padding-right "10px"}}
        [:i.zmdi.zmdi-city-alt]]
       "Back to query"])))

(defn annotation-panel []
  (let [marked-hits (re-frame/subscribe [:marked-hits {:has-marked? false}])
        open-hits (reagent/atom #{})]
    (fn []
      (if (zero? (count @marked-hits))
        [:div.container-fluid
         {:style {:width "100%" :padding "0 10px 0 10px" :margin-top "65px"}}
         [error-panel
          :status "No hits marked for annotation..."
          :status-content [back-to-query-button]]]
        [:div.container-fluid
         {:style {:width "100%" :padding "0 10px 0 10px" :margin-top "65px"}}
         [:div.row
          [:div.col-lg-12 [annotation-component marked-hits open-hits]]]
         [toolbar marked-hits open-hits]]))))
