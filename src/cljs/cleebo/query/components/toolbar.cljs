(ns cleebo.query.components.toolbar
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [taoensso.timbre :as timbre]
            [react-bootstrap.components :as bs]
            [goog.string :as gstr]
            [cleebo.utils :refer [->map by-id]]
            [cleebo.components :refer [dropdown-select]]
            [cleebo.query.components.annotation-modal
             :refer [annotation-modal-button]]))

(defn pager-button [& {:keys [direction label]}]
  [bs/button
   {:onClick #(re-frame/dispatch [:query-range direction :results-frame])
    :style {:font-size "12px" :height "34px"}}
   label])

(defn pager-buttons []
  [bs/button-toolbar
   [pager-button
    :direction :prev
    :label [:div [:i.zmdi.zmdi-arrow-left {:style {:margin-right "10px"}}] "prev"]] 
   [pager-button
    :direction :next
    :label [:div "next" [:i.zmdi.zmdi-arrow-right {:style {:margin-left "10px"}}]]]])

(defn on-click-sort [route]
  (fn []
    (re-frame/dispatch [:query-sort route :results-frame])))

(defn sort-buttons []
  (let [criterion (re-frame/subscribe [:session :query-opts :criterion])
        prop-name (re-frame/subscribe [:session :query-opts :prop-name])
        corpus (re-frame/subscribe [:session :query-opts :corpus])]
    (fn []
      [bs/button-toolbar
       {:justified true}
       [dropdown-select
        {:label "sort by: "
         :header "Select criterion"
         :model @criterion
         :options (map #(->map % %) ["match" "left-context" "right-context"])
         :select-fn (fn [k] (re-frame/dispatch [:set-session [:query-opts :criterion] k]))}]
       [dropdown-select
        {:label "sort prop: "
         :header "Select property"
         :options (map #(->map % %) ["word" "pos" "lemma"])
         :model @prop-name
         :select-fn (fn [k] (re-frame/dispatch [:set-session [:query-opts :prop-name] k]))}]
       [bs/button
        {:onClick (on-click-sort :sort-range)}
        "Sort page"]
       [bs/button
        {:onClick (on-click-sort :sort-query)}
        "Sort all"]])))

(defn query-result-label []
  (let [query-results (re-frame/subscribe [:session :query-results])]
    (fn []
      (let [{:keys [from to query-size]} @query-results]
        [:label
         {:style {:line-height "35px"}}
         (let [from (inc from) to (min to query-size)]
           (gstr/format "%d-%d of %d hits" from to query-size))]))))

(defn annotation-hit-button []
  (let [marked-hits (re-frame/subscribe [:marked-hits])]
    (fn []
      [bs/button
       {:bsStyle "primary"
        :style {:visibility (if (zero? (count @marked-hits)) "hidden" "visible")}
        :href "#/annotation"}
       "Annotate"])))

(defn annotation-button []
  [bs/button-toolbar
   [annotation-modal-button]])

(defn toolbar []
  (let [query-size (re-frame/subscribe [:query-results :query-size])]
    (fn []
      [:div.container-fluid
       {:style {:visibility (when (zero? @query-size) "hidden" "visible")}}
       [:div.row {:style {:margin-top "10px"}}
        [:div.col-lg-6
         [:div.row
          [:div.col-lg-4.pad [query-result-label]]
          [:div.col-lg-4.pad [pager-buttons]]
          [:div.col-lg-4.pad [annotation-button]]]]
        [:div.col-lg-6.pad
         [:div.pull-right [sort-buttons]]]]])))
