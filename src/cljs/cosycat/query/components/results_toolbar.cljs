(ns cosycat.query.components.results-toolbar
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [taoensso.timbre :as timbre]
            [react-bootstrap.components :as bs]
            [goog.string :as gstr]
            [cosycat.utils :refer [->default-map by-id ->map]]
            [cosycat.app-utils :refer [dekeyword ->int]]
            [cosycat.routes :refer [nav!]]
            [cosycat.components :refer [disabled-button-tooltip dropdown-select]]
            [cosycat.annotation.components.annotation-modal :refer [annotation-modal-button]]))

(defn pager-button [& {:keys [direction label]}]
  [bs/button
   {:onClick #(re-frame/dispatch [:query-range direction])
    :style {:font-size "12px" :height "34px" :width "70px"}}
   label])

(defn on-key-press [open? value]
  (fn [e]
    (when (= 13 (.-charCode e))
      (do (re-frame/dispatch [:query-from (dec (->int @value))])
          (swap! open? not)))))

(defn goto-button []
  (let [open? (reagent/atom false)
        value (reagent/atom 1)]
    (fn []
      [bs/dropdown-button
       {:title "goto"
        :onToggle #()
        :noCaret true
        :id "gotobutton"
        :style {:height "34px"}
        :open @open?
        :onClick #(swap! open? not)}
       [bs/menu-item
        {:eventKey "1"}
        [:input.form-control.form-control-no-border
         {:style {:height "30px"}
          :type "number"
          :min 1
          :autoFocus true               ;ensure blur onload
          :on-blur #(swap! open? not)
          :value @value         
          :on-change #(reset! value (.. % -target -value))
          :on-key-press (on-key-press open? value)
          :on-key-down #(.stopPropagation %)}]]])))

(defn pager-buttons []
  [bs/button-toolbar
   {:justified true}
   [pager-button
    :direction :prev
    :label [:div [:i.zmdi.zmdi-arrow-left {:style {:margin-right "10px"}}] "prev"]]
   [goto-button]
   [pager-button
    :direction :next
    :label [:div "next" [:i.zmdi.zmdi-arrow-right {:style {:margin-left "10px"}}]]]])

(defn query-result-label []
  (let [results-summary (re-frame/subscribe [:project-session :query :results-summary])]
    (fn []
      (let [{{from :from to :to} :page query-size :query-size} @results-summary]
        [:label
         {:style {:line-height "35px"}}
         (gstr/format "%d-%d of %d" (inc from) (min to query-size) query-size)]))))

(defn result-label-pager []
  (fn []
    [:div.container-fluid
     [:div.row
      [:div.col-lg-4.col-sm-4 [query-result-label]]
      [:div.col-lg-8.col-sm-8 [:div.pull-left [pager-buttons]]]]]))

(defn mark-hits-btn []
  [bs/button
   {:onClick #(re-frame/dispatch [:mark-hits])
    :style {:font-size "12px" :height "34px"}}
   "Mark hits"])

(defn token-field-button []
  (let [metadata-fields (re-frame/subscribe [:corpus-config :info :sort-props])
        current-field (re-frame/subscribe [:project-session :components :token-field])]
    (fn []
      [dropdown-select
       {:label "Prop: "
        :height "34px"
        :model (dekeyword @current-field)
        :options (->> @metadata-fields keys (map dekeyword) (mapv #(->map % %)))
        :select-fn #(re-frame/dispatch [:set-token-field (keyword %)])
        :header "Select prop to display"}])))

(defn toggle-discarded-button []
  (let [toggle-discarded (re-frame/subscribe [:project-session :components :toggle-discarded])]
    (fn []
      [bs/button
       {:onClick #(re-frame/dispatch [:toggle-project-session-component [:toggle-discarded]])
        :bsStyle (if-not @toggle-discarded "default" "primary")
        :style {:font-size "12px" :height "34px"}}
       "Toggle discarded"])))

(defn mark-buttons []
  (let [active-query (re-frame/subscribe [:project-session :components :active-query])]
    [bs/button-toolbar
     (when @active-query [toggle-discarded-button active-query])
     [token-field-button]
     [mark-hits-btn]
     [annotation-modal-button]]))

(defn results-toolbar []
  (fn []
    [:div.row
     [:div.col-md-6.col-sm-7 [result-label-pager]]
     [:div.col-md-6.col-sm-5 [:div.pull-right [mark-buttons]]]]))
