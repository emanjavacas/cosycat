(ns cleebo.settings.components.shared-components
  (:require [react-bootstrap.components :as bs]
            [cleebo.utils :refer [nbsp]]))

(defn row-component [& {:keys [label controllers help-text]}]
  (fn [& {:keys [label controllers help-text]}]
    [:div
     [:div.row.pull-left
      [bs/label {:style {:font-size "14px" :line-height "2.5em"}}
       label]]
     [:br]
     [:div.row [:hr]]
     [:div.row
      [:div.col-lg-5 controllers]]     
     [:div.row
      {:style {:margin-top "10px"}}
      [:div.col-lg-7.text-muted (str (nbsp 10) @help-text)]]
     [:br]]))
