(ns cleebo.settings.components.shared-components
  (:require [react-bootstrap.components :as bs]))

(defn row-component [& {:keys [label controllers help-text]}]
  (fn  [& {:keys [label controllers help-text]}]
    [:div
     [:div.row.pull-left
      [bs/label {:style {:font-size "14px" :line-height "2.5em"}}
       label]]
     [:br]
     [:div.row [:hr]]
     [:div.row
      [:div.col-lg-5
       controllers]
      [:div.col-lg-7.text-muted @help-text]]
     [:br]]))
