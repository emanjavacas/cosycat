(ns cosycat.query.components.annotate-query-modal
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [react-bootstrap.components :as bs]
            [cosycat.components :refer [dropdown-select]]
            [cosycat.utils :refer [->map]]
            [taoensso.timbre :as timbre]))

(defn validate-data [{:keys [query-id include-sort-opts? include-filter-opts?]}]
  (cond (empty? @query-id)               [:query-id "Empty query name"]
        (re-find #"[ \t\n\r]" @query-id) [:query-id "Disallowed whitespace"]
        (re-find #"[.-]"      @query-id) [:query-id "Disallowed characters \".\", \"-\""]))

(defn reset!-input-atoms [has-input-error? query-id description default]
  (doseq [validation-atom (vals has-input-error?)] (reset! validation-atom false))
  (reset! query-id "")
  (reset! description "")
  (reset! default "unseen"))

(defn on-dispatch
  [{:keys [query-id include-sort-opts? include-filter-opts? default description] :as data}
   has-input-error?]
  (fn []
    (if-let [[key error] (validate-data data)]
      (reset! (get has-input-error? key) error)
      (do (re-frame/dispatch
           [:query-new-metadata
            {:id @query-id
             :description @description
             :include-sort-opts? @include-sort-opts?
             :include-filter-opts? @include-filter-opts?
             :default @default}
            :on-dispatch #(reset!-input-atoms has-input-error? query-id description default)])
          ;; close modal
          (re-frame/dispatch [:close-modal :annotate-query])))))

(defn query-input-row [query-id has-input-error?]
  (fn [query-id has-input-error?]
    [:div.row
     [:div.form-group
      {:class (when @(:query-id has-input-error?) "has-error has-feedback")}
      [:div.input-group
       [:span.input-group-addon [bs/glyphicon {:glyph "tag"}]]
       [:input.form-control
        {:type "text"
         :required true
         :value @query-id
         :placeholder "Annotation query name"
         :on-key-down #(.stopPropagation %) ;avoid triggerring global events
         :on-change #(reset! query-id (.-value (.-target %)))}]]
      (when @(:query-id has-input-error?)
        [:span.help-block @(:query-id has-input-error?)])]]))

(defn query-description-row [description]
  (fn [description]
    [:div.row
     [:div.form-group
      [:div.input-group
       [:span.input-group-addon [bs/glyphicon {:glyph "pencil"}]]
       [:textarea.form-control
        {:type "text"
         :style {:overflow "hidden"
                 :max-height "100px"
                 :resize "vertical"}
         :maxLength 500
         :value @description
         :placeholder "Describe your query to other humans"
         :on-key-down #(.stopPropagation %) ;avoid triggerring global events
         :on-change #(reset! description (.-value (.-target %)))}]]]]))

(defn filter-checkbox-row [include-filter-opts? has-input-error?]
  (fn [include-filter-opts? has-input-error?]
    [:div.row
     [:div.checkbox
      [:label [:input {:type "checkbox"
                       :inline true
                       :value @include-filter-opts?
                       :on-change #(swap! include-filter-opts? not)}]
       "Save " [:strong "filters"] " along query?"]]]))

(defn sort-checkbox-row [include-sort-opts? has-input-error?]
  (fn [include-sort-opts? has-input-error?]
    [:div.row
     [:div.checkbox
      [:label [:input {:type "checkbox"
                       :inline true
                       :value @include-sort-opts?
                       :on-change #(swap! include-sort-opts? not)}]
       "Save " [:strong "sort options"] " along query?"]]]))

(defn default-dropdown [default]
  (fn [default]
    [dropdown-select
     {:label (if @default "Default value: " "Default value")
      :model @default
      :options (map #(->map % %) ["kept" "discarded" "unseen"])
      :header "Select default hit value"
      :select-fn #(reset! default %)}]))

(defn submit-button
  [{:keys [query-id include-sort-opts? include-filter-opts? description default]}
   has-input-error?]
  (fn [{:keys [query-id include-sort-opts? include-filter-opts? description default]}
       has-input-error?]
    [bs/button
     {:onClick (on-dispatch {:query-id query-id
                             :include-sort-opts? include-sort-opts?
                             :include-filter-opts? include-filter-opts?
                             :description description
                             :default default}
                            has-input-error?)
      :bsStyle "info"}
     "Create"]))

(defn annotate-query-modal []
  (let [show? (re-frame/subscribe [:modals :annotate-query])
        has-input-error? {:query-id (reagent/atom false)
                          :filter-opts (reagent/atom false)
                          :sort-opts (reagent/atom false)}
        query-id (reagent/atom "")
        description (reagent/atom "")
        include-sort-opts? (reagent/atom false)
        path-to-results [:project-session :query :results]
        sort-opts (re-frame/subscribe (into path-to-results [:results-summary :sort-opts]))
        include-filter-opts? (reagent/atom false)
        filter-opts (re-frame/subscribe (into path-to-results [:results-summary :filter-opts]))
        default (reagent/atom "unseen")]
    (fn []
      [bs/modal
       {:show @show?}
       [bs/modal-header
        {:closeButton true
         :onHide #(re-frame/dispatch [:close-modal :annotate-query])}
        [:h4 "New query annotation"]]
       [bs/modal-body
        [:div.container-fluid.text-justify
         [query-input-row query-id has-input-error?]
         [query-description-row description has-input-error?]
         (when-not (empty? @filter-opts) [filter-checkbox-row include-filter-opts? has-input-error?])
         (when-not (empty? @sort-opts) [sort-checkbox-row include-sort-opts? has-input-error?])
         [:div.row {:style {:height "15px"}}]]
        [bs/modal-footer
         [:div.pull-right
          [bs/button-toolbar
           [default-dropdown default]
           [submit-button
            {:query-id query-id
             :include-sort-opts? include-sort-opts?
             :include-filter-opts? include-filter-opts?
             :description description
             :default default}
            has-input-error?]]]]]])))
