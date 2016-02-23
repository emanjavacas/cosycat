(ns cleebo.query.components.toolbar
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [taoensso.timbre :as timbre]
            [react-bootstrap.components :as bs]
            [goog.string :as gstr]
            [cleebo.utils :refer [->map by-id make-annotation]]
            [cleebo.components :refer [dropdown-select]]
            [cleebo.query.components.annotation-popup :refer [annotation-popup]]
            [cleebo.query.logic :as q])
  (:require-macros [cleebo.env :as env :refer [cljs-env]]))

(defn pager-button [pager-fn label]
  (let [query-opts (re-frame/subscribe [:query-opts])
        query-results (re-frame/subscribe [:query-results])]
    (fn []
      [bs/button
       {:onClick #(let [{:keys [size corpus context]} @query-opts
                        {:keys [query-size from to]} @query-results
                        [from to] (pager-fn query-size size from to)]
                    (q/query-range corpus from to context))
        :style {:font-size "12px" :height "34px"}}
       label])))

(defn pager-buttons []
  [bs/button-toolbar
   [pager-button
    (fn [query-size size from to] (q/pager-prev query-size size from))
    [:div [:i.zmdi.zmdi-arrow-left {:style {:margin-right "10px"}}] "prev"]] 
   [pager-button
    (fn [query-size size from to] (q/pager-next query-size size to))
    [:div "next" [:i.zmdi.zmdi-arrow-right {:style {:margin-left "10px"}}]]]
   [bs/split-button
    {:title "Go!"
     :role "menuitem"
     :onClick #(timbre/debug %)}]])


(defn sort-buttons [query-opts query-results]
  (let [criterion (reagent/atom "match")
        prop-name (reagent/atom "word")]
    (fn [query-opts query-results]
      [bs/button-toolbar
       {:justified true}
       [dropdown-select
        {:label "sort by: "
         :header "Select criterion"
         :model @criterion
         :options (map #(->map % %) ["match" "left-context" "right-context"])
         :select-fn (fn [k] (reset! criterion k))}]
       [dropdown-select
        {:label "sort prop: "
         :header "Select property"
         :options (map #(->map % %) ["word" "pos" "lemma"])
         :model @prop-name
         :select-fn (fn [k] (reset! prop-name k))}]
       [bs/button
        {:disabled (not (some #{(:corpus @query-opts)} (:corpora (cljs-env :blacklab))))
         :onClick #(let [{:keys [corpus context size]} @query-opts
                         {:keys [from]} @query-results]
                     (q/query-sort corpus from (+ from size) context
                                   @criterion @prop-name :sort-range))}
        "Sort page"]
       [bs/button
        {:disabled (not (some #{(:corpus @query-opts)} (:corpora (cljs-env :blacklab))))
         :onClick #(let [{:keys [corpus context size]} @query-opts
                         {:keys [from]} @query-results]
                     (q/query-sort corpus from (+ from size) context
                                   @criterion @prop-name :sort-query))}
        "Sort all"]])))

(defn query-result-label [{:keys [from to query-size]}]
  (fn [{:keys [from to query-size]}]
    [:label
     {:style {:line-height "35px"}}
     (let [from (inc from) to (min to query-size)]
       (gstr/format "Displaying %d-%d of %d hits" from to query-size))]))

(defn annotation-hit-button []
  (let [marked-hits (re-frame/subscribe [:marked-hits])]
    (fn []
      [bs/button
       {:bsStyle "info"
        :style {:visibility (if (zero? (count @marked-hits)) "hidden" "visible")}
        :href "#/annotation"}
       "Annotate"])))

(defn annotation-token-button []
  (let [marked-tokens (re-frame/subscribe [:marked-tokens])]
    (fn []
      [annotation-popup marked-tokens])))

(defn annotation-button []
  [bs/button-toolbar
   [annotation-hit-button]
   [annotation-token-button]])

(defn toolbar []
  (let [query-opts (re-frame/subscribe [:query-opts])
        query-results (re-frame/subscribe [:query-results])]
    (fn []
      [:div.container-fluid
       {:style {:visibility (if (zero? (:query-size @query-results)) "hidden" "visible")}}
       [:div.row {:style {:margin-top "10px"}}
        [:div.col-lg-6
         [:div.row
          [:div.col-lg-4.pad [query-result-label @query-results]]
          [:div.col-lg-4.pad [pager-buttons]]
          [:div.col-lg-4.pad [annotation-button]]]]
        [:div.col-lg-6.pad
         [:div.pull-right [sort-buttons query-opts query-results]]]]])))
