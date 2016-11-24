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

(defn annotation-edit-message
  [{{{old-value :value key :key} :ann {doc :doc {:keys [B O] :as scope} :scope type :type} :span
     new-value :value corpus :corpus} :data by :by :as issue}]  
  [:div.container-fluid
   [:div.row
    [:div.col-lg-6.col-sm-6
     [:h5 [:span
           [:strong (capitalize by)]
           " suggest to change annotation key " [bs/label key]
           " from " [bs/label {:bsStyle "primary"} old-value]
           " to " [bs/label {:bsStyle "primary"} new-value] "."]]
     (let [tokens (if (= type "token") 1 (inc (- O B)))]
       [:h5 [:span
             "Annotation is in corpus " [:strong corpus]
             " in document " [:strong doc]
             " and spans " [:strong tokens] (if (> tokens 1) " tokens." " token.")]])]
    [:div.col-lg-6.col-sm-6
     [:div.pull-right
      [bs/button-toolbar
       [bs/button {:bsStyle "success"} "Accept"]
       [bs/button {:bsStyle "danger"} "Reject"]]]]]])

(defn annotation-edit-component [issue]
  (fn [issue]
    [:div.container-fluid
     ;; todo: add refresh button (new anns, more hit context, etc.)
     [:div.row [annotation-edit-message issue]]
     [:div.row {:style {:height "10px"}}]
     [:div.row [collapsible-issue-panel "Show hit" hit-component issue :show-hit]]
     [:div.row [issue-thread-component issue :collapsible? true]]]))
