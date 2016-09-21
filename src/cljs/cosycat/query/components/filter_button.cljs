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

(defn filter-input [has-focus?]
  (let [value (reagent/atom "")]
    (fn [has-focus?]
      [:input.form-control.form-control-no-border
       {:on-focus #(reset! has-focus? true)
        :on-blur #(reset! has-focus? false)
        :style {:height "20px" :margin-top "10px"}
        :value @value
        :on-key-down #(.preventDefault %)
        :on-key-press #(.log js/console "dispatch new filter")
        :on-change #(reset! value (.-value (.-target %)))}])))

(defn filter-field [{field-name :fieldName field-type :fieldType} filter-value]
  (let [has-focus? (reagent/atom false)]
    (fn [{field-name :fieldName field-type :fieldType} has-filter]
      [:div.well.well-sm
       {:style {:margin-bottom "5px"
                :min-width "60px"
                :min-height "70px"
                :box-shadow (->box (if (or @has-focus? filter-value) "#c8dde8" "#ffffff"))
                :background-color (when (or @has-focus? filter-value) "#e6f6ff")}}
       [:div.container-fluid
        [:div.row.text-muted field-name]
        [:div.row
         (if (or @has-focus? (not filter-value))
           [filter-input has-focus?]
           [:span
            {:on-click #(reset! has-focus? true)
             :style {:cursor "pointer"}}
            filter-value])]]])))

(defn current-filter [filter-opts field-name]
  (some #(= field-name (:attribute %)) filter-opts))

(defn filter-popover [{:keys [metadata filter-opts on-dispatch]}]
  [bs/popover
   {:id "popover"
    :style {:width "450px"}
    :title (reagent/as-component
            [:div.container-fluid
             [:div.row
              [:div.col-sm-8.pull-left [:h4 "Add filter to query"]]
              [:div.col-sm-4.text-right
               {:style {:font-size "12px" :text-align "right"}}
               [:span
                {:style {:cursor "pointer" :line-height "3.3"}
                 :onClick on-dispatch}
                "✕"]]]])}
   [:div.container-fluid
    (doall (for [[row-idx row] (map-indexed vector (partition-all 3 (vals metadata)))]
             ^{:key row-idx}
             [:div.row.pad
              (doall (for [{field-name :fieldName :as field} row
                           :let [{filter-value :value} (current-filter filter-opts field-name)]]
                       ^{:key field-name}
                       [:div.col-sm-4.col-md-4.pad [filter-field field "Hi!"]]))]))]])

(defn filter-button []
  (let [show? (reagent/atom false), target (reagent/atom nil)
        current-corpus-metadata (re-frame/subscribe [:corpus-config :info :corpus-info :metadata])
        filter-opts (re-frame/subscribe [:settings :query :filter-opts])]
    (fn []
      [:div
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
        (filter-popover
         {:metadata @current-corpus-metadata
          :filter-opts @filter-opts
          :on-dispatch #(swap! show? not)})]])))

