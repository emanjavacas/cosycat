(ns cleebo.query.components.toolbar
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [taoensso.timbre :as timbre]
            [react-bootstrap.components :as bs]
            [goog.string :as gstr]
            [cleebo.utils :refer [->map by-id make-annotation]]
            [cleebo.components :refer [dropdown-select]]
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

(defn input-row [marked-tokens]
  (fn [marked-tokens]
    [:tr
     ^{:key "key-input"}
     [:td
      [:input#token-ann-key.form-control
       {:type "text"
        :name "key-input"}]]
     ^{:key "value-input"}
     [:td
      [:input#token-ann-val.form-control
       {:type "text"
        :name "value-input"
        :on-key-press
        (fn [pressed]
          (if (= (.-charCode pressed) 13)
            (let [k (by-id "token-ann-key")
                  v (by-id "token-ann-val")]
              (doseq [{:keys [hit-num id]} @marked-tokens]
                (re-frame/dispatch
                 [:annotate
                  {:hit-num hit-num
                   :token-id id
                   :ann (make-annotation {k v})}])))))}]]]))

(defn inner-thead [k1 k2]
  [:thead
   [:tr
    [:th {:style {:padding-bottom "10px" :text-align "left"}}  k1]
    [:th {:style {:padding-bottom "10px" :text-align "right"}} k2]]])

(defn annotation-hit-button []
  (let [marked-hits (re-frame/subscribe [:marked-hits])]
    (fn []
      [bs/button
       {:bsStyle "info"
        :style {:visibility (if (zero? (count @marked-hits)) "hidden" "visible")}       
        :href "#/annotation"}
       "Annotate"])))

(defn token-annotation-table [marked-tokens]
  (fn [marked-tokens]
    [:table {:width "100%"}
     [:caption [:h4 "Annotation"]]
     (inner-thead "Key" "Value")
     [:tbody
      [input-row marked-tokens]]]))

(defn token-counts-table [marked-tokens]
  (fn [marked-tokens]
    [:table {:width "100%"}
     (inner-thead "Token" "Count")
     [:tbody
      {:style {:font-size "14px !important"}}
      (for [[word c] (frequencies (map :word @marked-tokens))]
        ^{:key (str word "pop")}
        [:tr
         [:td {:style {:padding-bottom "10px" :text-align "left"}} word]
         [:td {:style {:text-align "right"}}
          [bs/label c]]])]]))

(defn annotation-token-button []
  (let [marked-tokens (re-frame/subscribe [:marked-tokens])]
    (fn []
      [bs/overlay-trigger
       {:trigger "click"
        :rootClose true
        :placement "bottom"
        :overlay (reagent/as-component
                  [bs/popover
                   {:style {:min-width "500px"}
                    :title (reagent/as-component
                            [:span
                             {:style {:font-size "18px"}}
                             "Tokens marked for annotation"])}
                   ^{:key "cnt-table"} [token-counts-table marked-tokens]
                   [:hr]
                   ^{:key "ann-table"} [token-annotation-table marked-tokens]])}
       [bs/button
        {:bsStyle "info"
         :style {:visibility (if (zero? (count @marked-tokens)) "hidden" "visible")}}
        "Annotate Tokens"]])))

(defn annotation-button []
  [bs/button-toolbar
   [annotation-hit-button]
   [annotation-token-button]])

(defn toolbar []
  (let [query-opts (re-frame/subscribe [:query-opts])
        query-results (re-frame/subscribe [:query-results])]
    (fn []
      [:div.container-fluid
       {:style {:visibility (if-not (:query-size @query-results) "hidden" "visible")}}
       [:div.row {:style {:margin-top "10px"}}
        [:div.col-lg-6
         [:div.row
          [:div.col-lg-4.pad [query-result-label @query-results]]
          [:div.col-lg-4.pad [pager-buttons]]
          [:div.col-lg-4.pad [annotation-button]]]]
        [:div.col-lg-6.pad
         [:div.pull-right [sort-buttons query-opts query-results]]]]])))
