(ns cosycat.review.page
  (:require [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [react-bootstrap.components :as bs]
            [cosycat.review.components.query-toolbar :refer [query-toolbar]]
            [cosycat.review.components.results-toolbar :refer [results-toolbar]]
            [cosycat.review.components.results-frame :refer [results-frame]]
            [taoensso.timbre :as timbre]))

(defn review-panel-header []
  [:div.container-fluid
   [:div.row
    [:div.col-lg-10
     [:span.pull-left [:h4 "Review Frame"]]]]])

(defn review-panel []
  (let [results-summary (re-frame/subscribe [:project-session :review :results :results-summary])]
    (fn []
      [:div.container-fluid.pad
       {:style {:width "100%" :padding "0px 10px 0px 10px"}}
       [:div.row
        [bs/panel
         {:header (reagent/as-component [review-panel-header])}
         [:div.container-fluid
          [query-toolbar]
          [:div.row {:style {:height "20px"}}]
          (when-not (empty? @results-summary) [results-toolbar])
          (when-not (empty? @results-summary) [:div.row {:style {:height "20px"}}])
          (when-not (empty? @results-summary) [results-frame])]]]])))

