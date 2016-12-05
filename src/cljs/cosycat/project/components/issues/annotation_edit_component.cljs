(ns cosycat.project.components.issues.annotation-edit-component
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [react-bootstrap.components :as bs]
            [clojure.string :refer [capitalize]]
            [cosycat.utils :refer [format]]
            [cosycat.annotation.components.annotation-panel :refer [annotation-component]]
            [cosycat.project.components.issues.components :refer [collapsible-issue-panel]]
            [cosycat.project.components.issues.issue-thread-component
             :refer [issue-thread-component]]
            [taoensso.timbre :as timbre]))

(defn hit-component [{{hit-map :hit-map} :meta issue-id :id :as issue}]
  (let [color-map (re-frame/subscribe [:project-users-colors])]
    (reagent/create-class
     {:component-will-mount
      #(when-not hit-map (re-frame/dispatch [:fetch-issue-hit {:issue issue :context 6}]))
      :reagent-render
      (fn [{{new-value :value {:keys [value key]} :ann {{B :B :as scope} :scope} :span} :data
            {{:keys [hit]} :hit-map} :meta}]
        [annotation-component hit-map color-map
         :highlight-ann-key? key
         :highlight-token-id? (or B scope)
         :editable? false
         :show-match? false
         :show-hit-id? false])})))

(defn issue-resolve-buttons [issue-id & {:keys [commment]}]
  [bs/button-toolbar
   [bs/button
    {:bsStyle "success"
     :onClick #(re-frame/dispatch [:close-annotation-issue issue-id "accepted" ])}
    "Accept"]
   [bs/button
    {:bsStyle "danger"
     :onClick #(re-frame/dispatch [:close-annotation-issue issue-id "rejected"])}
    "Reject"]])

(defn close-issue-modal []
  (let [close-annotation-issue-modal (re-frame/subscribe [:modals :close-annotation-issue])
        comment (reagent/atom "")]
    (fn []
      [bs/modal
       {:show @close-annotation-issue-modal}
       [bs/modal-header
        {:closeButton true
         :onHide #(re-frame/dispatch [:close-modal :close-annotation-issue])}
        [:h4 "You want to close the issue?"]]
       [bs/modal-body
        [:div.container-fluid
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
              :value @comment
              :placeholder "Comment on your closing decision"
              :on-change #(reset! comment (.-value (.-target %)))}]]]]]]
       [bs/modal-footer
        [:div.pull-right
         [issue-resolve-buttons (:id @close-annotation-issue-modal) :comment @comment]]]])))

(defn annotation-edit-message
  [{{{old-value :value key :key} :ann {doc :doc {:keys [B O] :as scope} :scope type :type} :span
     new-value :value corpus :corpus} :data by :by issue-id :id :as issue}]  
  [:div.container-fluid
   [:div.row
    [:div.col-lg-8.col-sm-8
     [:h5 [:span
           [:strong (capitalize by)]
           " suggests to change annotation key " [bs/label key]
           " from value " [bs/label {:bsStyle "primary"} old-value]
           " to value " [bs/label {:bsStyle "primary"} new-value] "."]]
     (let [tokens (if (= type "token") 1 (inc (- O B)))]
       [:h5 [:span
             "Annotation is in corpus " [:strong corpus]
             " in document " [:strong doc]
             " and spans " [:strong tokens] (if (> tokens 1) " tokens." " token.")]])]
    [:div.col-lg-4.col-sm-4
     [:div.pull-right
      [bs/button
       {:onClick #(re-frame/dispatch [:open-modal :close-annotation-issue issue])}
       "Close issue"]]]]])

(defn annotation-edit-component [issue]
  (fn [issue]
    [:div.container-fluid
     ;; TODO: add refresh button (new anns, more hit context, etc.)
     [:div.row [annotation-edit-message issue]]
     [:div.row {:style {:height "10px"}}]
     [:div.row [collapsible-issue-panel "Show hit" hit-component issue :show-hit]]
     [:div.row [issue-thread-component issue :collapsible? true]]
     [close-issue-modal]]))
