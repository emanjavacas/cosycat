(ns cleebo.query.components.toolbar
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [taoensso.timbre :as timbre]
            [react-bootstrap.components :as bs]
            [goog.string :as gstr]
            [cleebo.utils :refer [->default-map by-id]]
            [cleebo.routes :refer [nav!]]
            [cleebo.components :refer
             [dropdown-select user-thumb filter-annotation-buttons disabled-button-tooltip]]
            [cleebo.query.components.annotation-modal :refer [annotation-modal-button]]))

(defn pager-button [& {:keys [direction label]}]
  [bs/button
   {:onClick #(re-frame/dispatch [:query-range direction :results-frame])
    :style {:font-size "12px" :height "34px" :width "70px"}}
   label])

(defn pager-buttons []
  [bs/button-toolbar
   {:justified true}
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

(defn query-result-label []
  (let [query-results (re-frame/subscribe [:session :query-results])]
    (fn []
      (let [{:keys [from to query-size]} @query-results]
        [:label
         {:style {:line-height "35px"}}
         (let [from (inc from) to (min to query-size)]
           (gstr/format "%d-%d of %d hits" from to query-size))]))))

(defn mark-all-hits-btn []
  [bs/button
   {:onClick #(re-frame/dispatch [:mark-all-hits])}
   "Mark hits"])

(defn unmark-all-hits-btn []
  [bs/button
   {:onClick #(re-frame/dispatch [:unmark-all-hits])}
   "Unmark hits"])

(defn annotation-hit-button []
  (let [marked-hits (re-frame/subscribe [:marked-hits])]
    (fn []
      (let [disabled? (fn [marked-hits] (zero? (count @marked-hits)))]
        [bs/overlay-trigger
         {:overlay (disabled-button-tooltip #(disabled? marked-hits) "No hits selected!")
          :placement "bottom"}
         [bs/button
          {:bsStyle "primary"
           :style (when (disabled? marked-hits) {:opacity 0.65 :cursor "auto"})
           :onClick #(when-not (disabled? marked-hits) (nav! "#/annotation"))}
          "Annotate hits"]]))))

(defn mark-buttons []
  [bs/button-toolbar
   [mark-all-hits-btn]
   [unmark-all-hits-btn]
   [annotation-hit-button]
   [annotation-modal-button]])

(defn toolbar []
  (let [query-size (re-frame/subscribe [:query-results :query-size])
        filtered-users (re-frame/subscribe [:session :active-project :filtered-users])]
    (fn []
      [:div.container-fluid
       {:style {:visibility (when (zero? @query-size) "hidden" "visible")}}
       [:div.row {:style {:margin-top "10px"}}
        [:div.col-lg-3.col-sm-3
         [:div.row
          [:div.col-lg-6.col-sm-5.pad [query-result-label]]
          [:div.col-lg-6.col-sm-7.pad [pager-buttons]]]]
        [:div.col-lg-9.col-sm-9.pad
         [:div.pull-right [sort-buttons]]]]
       [:div.row {:style {:margin-top "10px"}}
        [:div.col-lg-8.col-sm-8.pad [mark-buttons]]
        [:div.col-lg-4.col-sm-4.pad
         [:div.pull-right [filter-annotation-buttons]]]]])))
