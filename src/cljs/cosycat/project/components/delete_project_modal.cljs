(ns cosycat.project.components.delete-project-modal
  (:require [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [react-bootstrap.components :as bs]
            [cosycat.components :refer [user-profile-component compute-feedback]]
            [cosycat.roles :refer [project-user-roles]]
            [cosycat.utils :refer [human-time]]
            [cosycat.app-utils :refer [ceil pending-users]]
            [taoensso.timbre :as timbre]))

(defn pending-user-row [{:keys [label usernames]}]
  (fn [{:keys [label usernames]}]
    [:div.row
     {:style {:margin-bottom "-10px"}}
     [:span
      {:style {:font-size "18px"}}
      label
      [:h4.text-muted
       {:style {:display "inline-block" :margin-left "10px"}}
       (if-not (empty? usernames)
         (apply str (interpose ", " usernames))
         "_")]]]))

(def summary-help
  "In order to remove a project all users have to agree. 
   This summary shows the status of the collective decision.")

(defn pending-users-table [project]
  (fn [project]
    (let [{:keys [users]} project
          {:keys [pending non-app agreed-users]} (pending-users project)]
      [:div.container-fluid
       [:h4 "Summary"
        [:span [bs/overlay-trigger
                {:overlay (reagent/as-component [bs/tooltip {:id "tooltip"} summary-help])}
                [bs/glyphicon
                 {:glyph "question-sign"
                  :style {:font-size "14px" :margin-left "7px"}}]]]]
       [pending-user-row {:label "Pending users: " :usernames pending}]
       [pending-user-row {:label "Agreeing users: " :usernames agreed-users}]])))

(defn trigger-remove-project [project-name project-name-atom delete-project-modal-show]
  (fn [event]
    (when (and (= 13 (.-charCode event)) (= project-name @project-name-atom))
      (do (timbre/info "removing project" project-name)
          (swap! delete-project-modal-show not)
          (re-frame/dispatch [:project-remove {:project-name project-name}])))))

(defn project-name-input [project-name project-name-atom delete-project-modal-show]
  (fn [project-name project-name-atom delete-project-modal-show]
    [:div.row
     [:div.form-group
      {:class (compute-feedback project-name project-name-atom)}
      [:input.form-control
       {:value @project-name-atom
        :type "text"
        :on-key-press (trigger-remove-project
                       project-name project-name-atom delete-project-modal-show)
        :on-change #(reset! project-name-atom (.. % -target -value))}]]]))

(defn project-name-input-footer []
  [:div.row
   [:div.text-center
    [:div.pull-right "Type in the name of the project you wish to delete"]]])

(defn double-check-button [delete-project-modal-show project-name-input-show]
  [:div.row.pull-right
   [:div.text-center
    [bs/button-group
     [bs/button
      {:bsStyle "primary"
       :onClick #(swap! project-name-input-show not)}
      "Yes"]
     [bs/button
      {:onClick #(swap! delete-project-modal-show not)}
      "No"]]]])

(defn on-hide
  [project-name-atom project-name-input-show footer-alert-show delete-project-modal-show]
  (fn [e]
    (reset! project-name-atom "")
    (reset! project-name-input-show false)
    (js/setTimeout (fn [] (reset! footer-alert-show true)) 500)
    (swap! delete-project-modal-show not)))

(defn delete-project-modal-header [pending?]
  (fn [pending?]
    [bs/modal-header
     {:closeButton true}
     [bs/modal-title
      {:style {:font-size "18px"}}
      (if pending?
        "Do you really want to delete this project?"
        [:span "You have already agreed to remove this project"
         [:p.text-muted
          {:style {:font-size "15px"}}
          "Project will be deleted when all affected users agree."]])]]))

(defn delete-project-modal-footer [footer-alert-show]
  (fn [footer-alert-show]
    [bs/modal-footer
     [bs/alert
      {:bsStyle "danger" :onDismiss #(reset! footer-alert-show false)}
      "Remember that this operation is non reversible!"]]))

(defn delete-project-modal-body
  [{:keys [project-name-atom delete-project-modal-show project-name-input-show project pending?]}]
  (fn [{:keys [project-name-atom delete-project-modal-show project-name-input-show project pending?]}]
    (let [{project-name :name :as project} @project]
      [bs/modal-body
       [:div.container-fluid
        (when @project-name-input-show
          [project-name-input project-name project-name-atom delete-project-modal-show])
        (if-not @project-name-input-show [pending-users-table project])
        (if @project-name-input-show
          [project-name-input-footer]
          (when pending? [double-check-button delete-project-modal-show project-name-input-show]))]])))

(defn delete-project-modal [project-name delete-project-modal-show]
  (let [project-name-input-show (reagent/atom false)
        project-name-atom (reagent/atom "")
        footer-alert-show (reagent/atom true)
        project (re-frame/subscribe [:active-project])
        me (re-frame/subscribe [:me :username])]
    (fn [project-name delete-project-modal-show]
      (let [{:keys [pending]} (pending-users @project)
            pending? (contains? (apply hash-set pending) @me)]
        [bs/modal
         {:show @delete-project-modal-show
          :onHide (on-hide project-name-atom project-name-input-show
                           footer-alert-show delete-project-modal-show)}
         [delete-project-modal-header pending?]
         [delete-project-modal-body
          {:project project
           :project-name-atom project-name-atom
           :delete-project-modal-show delete-project-modal-show
           :project-name-input-show project-name-input-show
           :pending? pending?}]
         (when (and @project-name-input-show @footer-alert-show)
           [delete-project-modal-footer footer-alert-show])]))))
