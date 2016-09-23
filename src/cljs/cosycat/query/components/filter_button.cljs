(ns cosycat.query.components.filter-button
  (:require [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [react-bootstrap.components :as bs]
            [cosycat.components :refer [dropdown-select]]
            [cosycat.utils :refer [->default-map]]
            [cosycat.app-utils :refer [dekeyword]]
            [taoensso.timbre :as timbre]))

(defn ->box [color]
  (str "0 -1.5px " color " inset"))

(defn ->filter-opt [filter-name filter-value]
  {:attribute filter-name :value filter-value})

(defn add-filter [filter-name filter-value]
  (re-frame/dispatch [:add-opts-map :filter-opts (->filter-opt filter-name filter-value)]))

(defn remove-filter [filter-name]
  (let [update-f (fn [filter-opts] (->> filter-opts (remove #(= filter-name (:attribute %))) vec))]
    (re-frame/dispatch [:remove-opts-map :filter-opts :update-f update-f])))

(defn dispatch-new-filter [filter-name value]
  (fn [e]
    (when (= 13 (.-charCode e))
      (add-filter filter-name @value))))

(def span-height "40px")

(defn filter-input [filter-name clicked?]
  (let [value (reagent/atom "")]
    (fn [filter-name clicked?]
      [:input.form-control.form-control-no-border
       {:on-blur #(reset! clicked? false)
        :style {:height span-height :margin-top "10px"}
        :value @value
        :autoFocus true
        :on-key-down #(.stopPropagation %)
        :on-key-press (dispatch-new-filter filter-name value)
        :on-change #(reset! value (.-value (.-target %)))}])))

(defn value-span [filter-name filter-value {:keys [clicked? has-value?]}]
  (fn [filter-name filter-value {:keys [clicked? has-value?]}]
    [:div.text-muted
     {:on-click #(do (swap! clicked? not) (when has-value? (remove-filter filter-name)))
      :style {:cursor "pointer" :height span-height :margin-top "10px"}}
     filter-value]))

(defn filter-field [{filter-name :fieldName field-type :fieldType} filter-value]
  (let [clicked? (reagent/atom false)]
    (fn [{filter-name :fieldName field-type :fieldType} filter-value]
      [:div
       [:div.well.well-sm
        {:style {:margin-bottom "5px"
                 :min-width "60px"
                 :min-height "70px"
                 :box-shadow (->box (if (or @clicked? filter-value) "#c8dde8" "#ffffff"))
                 :background-color (when (or @clicked? filter-value) "#e6f6ff")}}
        [:div.container-fluid
         [:div.row filter-name]
         [:div.row
          (cond (and @clicked? (not filter-value)) [filter-input filter-name clicked?]
                filter-value [value-span filter-name filter-value {:clicked? clicked? :has-value? true}]
                :else [value-span filter-name "..." {:clicked? clicked? :has-value? false}])]]]])))

(defn current-filter [filter-opts filter-name]
  (some #(when (= filter-name (:attribute %)) %) filter-opts))

(defn filter-button []
  (let [show? (reagent/atom false), target (reagent/atom nil)
        corpus-metadata (re-frame/subscribe [:corpus-config :info :corpus-info :metadata])
        filter-opts (re-frame/subscribe [:settings :query :filter-opts])]
    (fn []
      (.log js/console @filter-opts)
      [:div.text-right
       [bs/button
        {:onClick #(do (swap! show? not) (reset! target (.-target %)))
         :bsStyle "primary"}
        "Add Filter"]
       [bs/overlay
        {:show @show?
         :target (fn [] @target)
         :placement "left"
         :rootClose true
         :onHide #(swap! show? not)}
        [bs/popover
         {:id "popover"
          :style {:min-width "450px"}
          :title (reagent/as-component
                  [:div.container-fluid
                   [:div.row
                    [:div.col-sm-8.pull-left [:h4 "Add filter to query"]]
                    [:div.col-sm-4.text-right
                     {:style {:font-size "12px" :text-align "right"}}
                     [:span
                      {:style {:cursor "pointer" :line-height "3.3"}
                       :onClick #(swap! show? not)}
                      "✕"]]]])}
         [:div.container-fluid
          (doall (for [[row-idx row] (map-indexed vector (partition-all 3 (vals @corpus-metadata)))]
                   ^{:key row-idx}
                   [:div.row.pad
                    (doall (for [{filter-name :fieldName :as field} row
                                 :let [{filter-value :value} (current-filter @filter-opts filter-name)]]
                             ^{:key filter-name}
                             [:div.col-sm-4.col-md-4.pad [filter-field field filter-value]]))]))]]]])))

