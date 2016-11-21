(ns cosycat.project.components.issues.components
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [react-bootstrap.components :as bs]
            [taoensso.timbre :as timbre]))

(defn collapsible-issue-panel [header child {issue-id :id :as issue} panel-id]
  (let [expanded? (re-frame/subscribe [:project-session :components :issues issue-id panel-id])]
    (fn [header child issue panel-id]
      [:div.panel.panel-default
       {:style {:margin-bottom "5px"}}
       [:div.panel-heading
        {:on-click #(re-frame/dispatch [:toggle-project-session-component [:issues issue-id panel-id]])
         :style {:font-size "16px" :cursor "pointer"}}
        header]
       (when @expanded? [:div.panel-body [child issue]])])))
