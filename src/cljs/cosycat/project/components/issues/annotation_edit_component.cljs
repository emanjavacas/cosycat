(ns cosycat.project.components.issues.annotation-edit-component
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [react-bootstrap.components :as bs]
            [cosycat.project.components.issues.components :refer [collapsible-issue-panel]]
            [cosycat.project.components.issues.issue-thread-component
             :refer [issue-thread-component]]
            [taoensso.timbre :as timbre]))

(defn hit-component [{{hit-map :hit-map} :meta :as issue}]
  (reagent/create-class
   {:component-will-mount
    #(when-not hit-map (re-frame/dispatch [:fetch-issue-hit {:issue issue :context 10}]))
    :reagent-render
    (fn [{{new-value :value {:keys [value key]} :ann} :data {{:keys [hit]} :hit-map} :meta}]
      [:div (doall (for [{:keys [id word match]} hit]
                     ^{:key id}
                     [:span
                      {:style {:padding "0 5px"}}
                      (if match [:strong word] word)]))])}))

(defn annotation-edit-component [issue]
  (fn [issue]
    [:div.container-fluid
     [:div.row [collapsible-issue-panel "Show hit" hit-component issue :show-hit]]
     [:div.row [issue-thread-component issue :collapsible? true]]]))
