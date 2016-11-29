(ns cosycat.project.page
  (:require [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [react-bootstrap.components :as bs]
            [cosycat.project.components.delete-project-modal :refer [delete-project-modal]]
            [cosycat.project.components.leave-project-modal :refer [leave-project-modal]]
            [cosycat.project.components.users.users-frame :refer [users-frame]]
            [cosycat.project.components.events.events-frame :refer [events-frame]]
            [cosycat.project.components.issues.issues-frame :refer [issues-frame]]
            [cosycat.project.components.queries.queries-frame :refer [queries-frame]]
            [cosycat.components :refer [user-profile-component]]
            [cosycat.roles :refer [project-user-roles]]
            [cosycat.utils :refer [human-time format]]
            [cosycat.app-utils :refer [ceil dekeyword]]
            [cosycat.viewport :refer [viewport]]
            [taoensso.timbre :as timbre]))

(defn can-delete-project? [my-role]
  (not= my-role "guest"))

(defn delete-project-btn []
  (let [show? (re-frame/subscribe [:modals :delete-project])]
    (fn []
      [bs/overlay-trigger
       {:overlay (reagent/as-component [bs/tooltip {:id "tooltip"} "Remove project"])
        :placement "bottom"}
       [bs/button
        {:bsSize "small"
         :onClick #(re-frame/dispatch [(if @show? :close-modal :open-modal) :delete-project])}
        [bs/glyphicon {:glyph "remove-sign"}]]])))

(defn leave-project-btn []
  (let [show? (re-frame/subscribe [:modals :leave-project])]
    (fn []
      [bs/overlay-trigger
       {:overlay (reagent/as-component [bs/tooltip {:id "tooltip"} "Leave project"])
        :placement "bottom"}
       [bs/button
        {:bsSize "small"
         :onClick #(re-frame/dispatch [(if @show? :close-modal :open-modal) :leave-project])}
        [bs/glyphicon {:glyph "hand-right"}]]])))

(defn project-buttons [my-role]
  (fn [my-role]
    [bs/button-group
     (when (can-delete-project? @my-role) [delete-project-btn])
     [leave-project-btn]]))

(defn project-header [{:keys [name description created creator] :as project}]
  (let [my-role (re-frame/subscribe [:active-project-role])]
    (fn [{:keys [name description created creator] :as project}]
      [:div.container-fluid
       [:div.row
        [:div.col-lg-6.col-md-6.col-sm-6
         [:div.container-fluid
          [:div.row {:style {:font-size "32px"}} name]
          [:div.row {:style {:margin-top "15px"}} [:p description]]
          [:div.row
           [:p.text-muted
            "Created by " [:strong creator] " on " [:span (human-time created)]]]]]
        [:div.col-lg-6 [:div.pull-right [project-buttons my-role]]]]
       [:div.row [:div.col-lg-12 [:hr]]]])))

(defmulti project-frame identity)
(defmethod project-frame :users [] [#'users-frame])
(defmethod project-frame :events [] [#'events-frame])
(defmethod project-frame :queries [] [#'queries-frame])
(defmethod project-frame :issues [] [#'issues-frame])
(defmethod project-frame :default []
  [:div.container-fluid
   [:div.row
    {:style {:margin-top "50px"}}
    [:div.col-lg-3]
    [:div.col-lg-6.text-center
     [:div [:h3 "Not implemented yet!"]]]
    [:div.col-lg-3]]])

(defn pill [key active-frame & {:keys [notifications]}]
  (fn [key active-frame & {:keys [notifications] :or {notifications 0}}]
    [:li {:class (when (= key @active-frame) "active") :style {:cursor "pointer"}}
     [:a {:onClick #(re-frame/dispatch [:set-active-project-frame key])}
      (clojure.string/capitalize (dekeyword key))
      (when (> notifications 0) [:span.badge notifications])]]))

(defn project-panel []
  (let [active-project (re-frame/subscribe [:active-project])
        active-project-frame (re-frame/subscribe [:project-session :components :active-project-frame])]
    (fn []
      (let [{:keys [name issues] :as project} @active-project]
        [:div
         [delete-project-modal name]
         [leave-project-modal name]
         [:div.container-fluid
          [:div.row [project-header project]]
          [:div.row
           [:div.col-lg-12
            [:ul.nav.nav-pills
             [pill :users active-project-frame]
             [pill :events active-project-frame]
             [pill :queries active-project-frame]
             [pill :issues active-project-frame
              :notifications (count (filter #(= (:status %) "open") (vals issues)))]]]]
          [:div.row {:style {:margin-top "20px"}}]
          [:div.row (project-frame @active-project-frame)]]]))))
