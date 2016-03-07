(ns cleebo.annotation.page
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [react-bootstrap.components :as bs]
            [cleebo.components :refer [error-panel]]
            [cleebo.annotation.components.control-panel :refer [control-panel]]
            [cleebo.annotation.components.annotation-component :refer [annotation-component]]
            [cleebo.annotation.components.annotation-queue :refer [annotation-queue]]))

(defn back-to-query-button []
  [bs/button {:href "#/query"}
   [:span {:style {:padding-right "10px"}}
    [:i.zmdi.zmdi-city-alt]]
   "Back to query"])

(defn annotation-panel []
  (let [marked-hits (re-frame/subscribe [:marked-hits {:has-marked? false}])
        current-hit (reagent/atom 0)
        current-token (reagent/atom 0)]
    (fn []
      [:div.container-fluid
       {:style {:width "100%" :padding "0 10px 0 10px"}}
       (if (zero? (count @marked-hits))
         [error-panel
          :status "No hits marked for annotation..."
          :status-content [back-to-query-button]]
         [:div
          [:div.row
           {:style {:height "30%"}}
           [:div.col-lg-12 [annotation-component marked-hits current-hit current-token]]]
          [:div.row
           [:div.col-lg-8 [annotation-queue marked-hits current-hit current-token]]
           [:div.col-lg-4.col-lg-offset-0
            [control-panel marked-hits current-hit current-token]]]])])))
