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
  (let [criterion (re-frame/subscribe [:settings :query :query-opts :criterion])
        attribute (re-frame/subscribe [:settings :query :query-opts :attribute])
        corpus (re-frame/subscribe [:settings :query :corpus])]
    (fn []
      [bs/button-toolbar
       {:justified true}
       [dropdown-select
        {:label "sort by: "
         :header "Select criterion"
         :model @criterion
         :options (->default-map ["match" "left-context" "right-context"])
         :select-fn #(re-frame/dispatch [:set-settings [:query :sort-context-opts :attribute] %])}]
       [dropdown-select
        {:label "sort prop: "
         :header "Select property"
         :options (->default-map ["word" "pos" "lemma"]);[TODO:This is corpus-dependent]
         :model @attribute
         :select-fn #(re-frame/dispatch [:set-settings [:query :sort-match-opts :attribute] %])}]
       [bs/button
        {:onClick (on-click-sort :sort-query)}
        "Sort"]])))

(defn sort-toolbar []
  (let [sort-opts (re-frame/subscribe [:settings :query :sort-opts])
        corpus (re-frame/subscribe [:settings :query :corpus])]
    (fn []
      [:div.row
       [:div.col-lg-12.pull-left [sort-buttons]]])))
