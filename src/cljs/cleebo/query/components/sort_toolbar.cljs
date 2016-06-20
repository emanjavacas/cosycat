(ns cleebo.query.components.sort-toolbar
  (:require [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [react-bootstrap.components :as bs]
            [cleebo.components :refer [dropdown-select]]
            [cleebo.utils :refer [->default-map]]))

(defn on-click-sort [route]
  (fn []
    (re-frame/dispatch [:query-sort route :results-frame])))

(defn sort-buttons []
  (let [criterion (re-frame/subscribe [:session :query-opts :criterion])
        attribute (re-frame/subscribe [:session :query-opts :attribute])
        corpus (re-frame/subscribe [:session :query-opts :corpus])]
    (fn []
      [bs/button-toolbar
       {:justified true}
       [dropdown-select
        {:label "sort by: "
         :header "Select criterion"
         :model @criterion
         :options (->default-map ["match" "left-context" "right-context"])
         :select-fn (fn [k] (re-frame/dispatch [:set-session [:query-opts :criterion] k]))}]
       [dropdown-select
        {:label "sort prop: "
         :header "Select property"
         :options (->default-map ["word" "pos" "lemma"]);[TODO:This is corpus-dependent]
         :model @attribute
         :select-fn (fn [k] (re-frame/dispatch [:set-session [:query-opts :attribute] k]))}]
       [bs/button
        {:onClick (on-click-sort :sort-range)}
        "Sort page"]
       [bs/button
        {:onClick (on-click-sort :sort-query)}
        "Sort all"]])))

(defn sort-toolbar []
  [:div.row
   [:div.col-lg-12.pull-left [sort-buttons]]])
