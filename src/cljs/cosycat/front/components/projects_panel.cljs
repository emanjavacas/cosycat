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
      [:td
       {:style {:padding-right "10px"}}
       [bs/overlay-trigger
        {:overlay (reagent/as-component [bs/tooltip {:id "tooltip"} username])
         :placement "bottom"
         :class "pull-right"}
        [user-selection-component @user-info]]])))

(defn users-row [creator users]
  (fn [creator users]
    [:table [:tbody [:tr (doall (for [{username :username :as user} users]
                                  ^{:key username} [user-cell user]))]]]))

(defn spacer [& {:keys [height] :or {height 5}}]
  [:div.row {:style {:height (str height "px")}}])

(defn project-row [{:keys [name description users created creator]}]
  (let [creator-info (re-frame/subscribe [:user creator])]
    (fn [{:keys [name description users created]}]
      [bs/list-group-item
       (reagent/as-component
        [:div.container-fluid
         [:div.row
          [:div.col-lg-8
           [:h4 [:a {:style {:cursor "pointer"} :on-click #(nav! (str "/project/" name))} name]]
           [:div description]]]
         [spacer :height 20]
         [:div.row
          [:div.col-lg-3 [:span.text-muted "Created by: "]]
          [:div.col-lg-9 [user-selection-component @creator-info]]]
         [spacer]
         [:div.row
          [:div.col-lg-3 [:span.text-muted "Users in project: "]]
          [:div.col-lg-9 [users-row creator users]]]
         [spacer :height 10]
         [:div.row
          [:div.col-lg-3 [:span.text-muted "Created on: "]]
          [:div.col-lg-9 [:span (human-time created)]]]
         [spacer]])])))

(defn projects-panel [projects]
  (fn [projects]
    [:div.container-fluid
     [:div.row
      [bs/list-group
       (doall (for [[name project] @projects]
                ^{:key (str name)} [project-row project]))]]]))
