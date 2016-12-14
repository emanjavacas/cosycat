(ns cosycat.review.page
  (:require [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [react-bootstrap.components :as bs]
            [cosycat.review.review-toolbar :refer [review-toolbar]]
            [taoensso.timbre :as timbre]))

(defn review-panel-header []
  [:div.container-fluid
   [:div.row
    [:div.col-lg-10
     [:span.pull-left [:h4 "Review Frame"]]]]])

(defn review-panel []
  [:div.container-fluid.pad
   {:style {:width "100%" :padding "0px 10px 0px 10px"}}
   [:div.row
    [bs/panel
     {:header (reagent/as-component [review-panel-header])}
     [:div.container-fluid
      [review-toolbar]]]]])

