(ns cosycat.front.components.projects-panel
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [react-bootstrap.components :as bs]
            [cosycat.routes :refer [nav!]]
            [cosycat.utils :refer [human-time]]
            [cosycat.components :refer [user-selection-component]]
            [cosycat.front.components.new-project-panel :refer [new-project-btn]]
            [taoensso.timbre :as timbre]))

(defn user-cell [{username :username}]
  (let [user-info (re-frame/subscribe [:user username])]
    (fn [user]
      [user-selection-component @user-info])))

(defn users-row [creator users]
  (fn [creator users]
    [:table
     [:tbody
      [:tr
       (doall (for [{username :username :as user} users]
                ^{:key username}
                [:td
                 {:style {:padding "8px 8px 8px 0px !important"}}
                 [user-cell user]]))]]]))

(defn spacer [& {:keys [height] :or {height 5}}]
  [:div.row {:style {:height (str height "px")}}])

(defn issues-badge [issues]
  (fn [issues]
    (let [open-issues (count (filter #(= "open" (:status %)) (vals issues)))]    
      [:span.badge (when (pos? open-issues) open-issues)])))

(defn project-row [{:keys [name description users created creator issues]}]
  (let [creator-info (re-frame/subscribe [:user creator])]
    (fn [{:keys [name description users created issues]}]
      [bs/list-group-item
       (reagent/as-component
        [:div.container-fluid
         [:div.row
          [:div.col-lg-8.col-sm-8
           [:h3 [:a {:style {:cursor "pointer"} :on-click #(nav! (str "/project/" name))} name]]
           [:span.text-muted "Created on " (human-time created)]
           [:h4 description]]
          [:div.col-lg-4.col-sm-4.text-right
           [issues-badge issues]]]
         [spacer :height 20]
         [:div.row
          [:div.col-lg-3.col-md-3.col-sm-3 [:span.text-muted "Created by: "]]
          [:div.col-lg-9.col-md-9.col-sm-9 [user-selection-component @creator-info]]]
         [spacer]
         [:div.row
          [:div.col-lg-3.col-md-3.col-sm-3 [:span.text-muted "Users in project: "]]
          [:div.col-lg-9.col-md-9.col-sm-9
           {:style {:overflow-x "auto"}}
           [users-row creator users]]]
         [spacer]])])))

(defn projects-subpanel [projects]
  (fn [projects]
    [:div.container-fluid
     [:div.row
      [bs/list-group
       (doall (for [[name project] @projects]
                ^{:key (str name)} [project-row project]))]]]))

(defn no-projects []
  [:div [:p "You don't have current projects. Start one right now."]])

(defn projects-panel []
  (let [projects (re-frame/subscribe [:projects])]
    (fn []
      (if (zero? (count @projects))
        [:div [no-projects]]
        [:div [projects-subpanel projects]]))))
