(ns cosycat.query.components.annotate-query-modal
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [react-bootstrap.components :as bs]
            [cosycat.components :refer [dropdown-select]]
            [cosycat.utils :refer [->map]]
            [taoensso.timbre :as timbre]))

(defn validate-query-name [query-id]
  (cond (re-find #"[ \t\n\r]" query-id) "Disallowed whitespace"
        (re-find #"[.-]"      query-id) "Disallowed characters \".\", \"-\""))

(defn on-dispatch
  [{:keys [query-id include-sort-opts? include-filter-opts? default]} has-input-error?]
  (fn []
    (if-let [error (validate-query-name @query-id)]
      (reset! has-input-error? error)
      (do (re-frame/dispatch
           [:query-new-metadata
            {:id @query-id
             :include-sort-opts? @include-sort-opts?
             :include-filter-opts? @include-filter-opts?
             :default @default}])
          (re-frame/dispatch [:close-modal :annotate-query])))))

(defn annotate-query-modal []
  (let [show? (re-frame/subscribe [:modals :annotate-query])
        has-input-error? (reagent/atom false)
        query-id (reagent/atom "")
        include-sort-opts? (reagent/atom false)
        include-filter-opts? (reagent/atom false)
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
         [:div.row
          [:div.form-group
           {:class (when (and (not (empty? @query-id)) @has-input-error?) "has-error has-feedback")}
           [:div.input-group
            [:span.input-group-addon [bs/glyphicon {:glyph "tag"}]]
            [:input.form-control
             {:type "text"
              :value @query-id
              :placeholder "Annotation query name"
              :on-change #(reset! query-id (.-value (.-target %)))}]]
           (when (and (not (empty? @query-id)) @has-input-error?) [:span.help-block @has-input-error?])]]
         [:div.row
          [:div.checkbox
           [:label [:input {:type "checkbox"
                            :inline true
                            :value @include-filter-opts?
                            :on-change #(swap! include-filter-opts? not)}]
            "Save " [:strong "filters"] " along query?"]]]
         [:div.row
          [:div.checkbox
           [:label [:input {:type "checkbox"
                            :inline true
                            :value @include-sort-opts?
                            :on-change #(swap! include-sort-opts? not)}]
            "Save " [:strong "sort options"] " along query?"]]]
         [:div.row {:style {:height "15px"}}]]
        [bs/modal-footer
         [:div.pull-right
          [bs/button-toolbar
           [dropdown-select
            {:label (if @default "Default value: " "Default value")
             :model @default
             :options (map #(->map % %) ["kept" "discarded" "unseen"])
             :header "Select default hit value"
             :select-fn #(reset! default %)}]
           [bs/button
            {:onClick (on-dispatch
                       {:query-id query-id
                        :include-sort-opts? include-sort-opts?
                        :include-filter-opts? include-filter-opts?
                        :default default}
                       has-input-error?)
             :bsStyle "info"}
            "Create"]]]]]])))

