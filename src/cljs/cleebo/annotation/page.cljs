(ns cleebo.annotation.page
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [react-bootstrap.components :as bs]
            [cleebo.components :refer [error-panel]]
            [cleebo.annotation.components.annotation-component
             :refer [annotation-component]]))

(defn back-to-query-button []
  [bs/button {:href "#/query"}
   [:span {:style {:padding-right "10px"}}
    [:i.zmdi.zmdi-city-alt]]
   "Back to query"])

(defn annotation-panel []
  (let [marked-hits (re-frame/subscribe [:marked-hits {:has-marked? false}])]
    (.log js/console @marked-hits)
    (fn []
      [:div.container-fluid
       {:style {:width "100%" :padding "0 10px 0 10px"}}
       (if (zero? (count @marked-hits))
         [error-panel
          :status "No hits marked for annotation..."
          :status-content [back-to-query-button]]
         [:div.row
          [:div.col-lg-12 [annotation-component marked-hits]]])])))
