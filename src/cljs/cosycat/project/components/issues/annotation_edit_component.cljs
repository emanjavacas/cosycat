(ns cosycat.project.components.issues.annotation-edit-component
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [react-bootstrap.components :as bs]
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

(defn annotation-edit-panel [header child {issue-id :id :as issue} panel-id]
  (let [expanded? (re-frame/subscribe [:project-session :components :issues issue-id panel-id])]
    (fn [header child issue panel-id]
      [:div.panel.panel-default
       {:style {:margin-bottom "5px"}}
       [:div.panel-heading
        {:on-click #(re-frame/dispatch [:toggle-project-session-component [:issues issue-id panel-id]])
         :style {:font-size "16px" :cursor "pointer"}}
        header]
       (when @expanded? [:div.panel-body [child issue]])])))

(defn annotation-edit-component [issue]
  (fn [issue]
    [:div.container-fluid
     [annotation-edit-panel "Show hit" hit-component issue :show-hit]
     [annotation-edit-panel "Show thread" issue-thread-component issue :show-thread]]))
