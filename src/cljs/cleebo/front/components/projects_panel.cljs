(ns cleebo.front.components.projects-panel
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [react-bootstrap.components :as bs]
            [cleebo.routes :refer [nav!]]
            [cleebo.components :refer [user-selection-component]]
            [cleebo.front.components.new-project-panel :refer [new-project-btn]]
            [taoensso.timbre :as timbre]))

(defn user-cell [user]
  (fn [{username :username}]
    [:td
     {:style {:padding-right "10px"}}
     [bs/overlay-trigger
      {:overlay (reagent/as-component [bs/tooltip {:id "tooltip"} username])
       :placement "bottom"
       :class "pull-right"}
      [user-selection-component {:username username}]]]))

(defn users-row [creator users]
  (fn [creator users]
    [:table [:tbody [:tr (doall (for [{username :username :as user} users
                                      :when (not= username creator)]
                                  ^{:key username} [user-cell user]))]]]))

(defn project-row [{:keys [name description creator users]}]
  (fn [{:keys [name description creator users]}]
    [bs/list-group-item
     {:onClick #(nav! (str "/project/" name))}
     (reagent/as-component
      [:div.container-fluid
       [:div.row
        [:div.col-lg-8 [:p name]]]
       [:div.row
        [:div.col-lg-3 [:span.text-muted "Created by: "]]
        [:div.col-lg-9 [user-selection-component {:username creator}]]]
       [:div.row {:style {:height "5px"}}]
       [:div.row
        [:div.col-lg-3 [:span.text-muted "Users in project: "]]
        [:div.col-lg-9 [users-row creator users]]]
       [:div.row {:style {:height "5px"}}]
       [:div.row
        [:div.col-lg-3 [:span.text-muted "Project description: "]]
        [:div.col-lg-9 description]]])]))

(defn projects-panel [projects]
  (let [db-users (re-frame/subscribe [:session :users])]
    (fn [projects]
      [:div.container-fluid
       [:div.row
        [bs/list-group
         (doall (for [{:keys [name] :as project} @projects]
                  ^{:key (str name)} [project-row project]))]]])))
