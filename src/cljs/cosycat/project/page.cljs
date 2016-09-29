(ns cosycat.project.page
  (:require [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [react-bootstrap.components :as bs]
            [cosycat.project.components.delete-project-modal :refer [delete-project-modal]]
            [cosycat.project.components.leave-project-modal :refer [leave-project-modal]]
            [cosycat.project.components.add-user-modal :refer [add-user-modal]]
            [cosycat.components :refer [user-profile-component]]
            [cosycat.roles :refer [project-user-roles]]
            [cosycat.utils :refer [human-time format]]
            [cosycat.app-utils :refer [ceil]]
            [cosycat.viewport :refer [viewport]]
            [taoensso.timbre :as timbre]))

(def users-per-row 3)

(def my-user-style {:border "1px solid #d1e8f1" :background-color "#eff7fa"})

(defn col-class [users-per-row] "col-lg-6.col-sm-6.col-md-6")

(defn can-edit-role? [my-role target-role]
  (cond (some #{target-role} ["project-lead" "creator"]) false
        (some #{my-role} ["guest" "user"]) false
        :else true))

(defn can-delete-project? [my-role]
  (not= my-role "guest"))

(defn can-add-users? [my-role]
  (contains? #{"project-lead" "creator"} my-role))

(defn project-user [{:keys [username]} project-role my-role]
  (let [user (re-frame/subscribe [:user username])]
    (fn [{:keys [username]} project-role my-role]
      [user-profile-component @user project-user-roles
       :role project-role
       ;; :on-submit TODO: send edit to project
       :on-submit #(.log js/console % %2)
       :displayable? true
       :editable? (can-edit-role? my-role project-role)])))

(defn project-users [users my-role]
  (let [me (re-frame/subscribe [:me :username])]
    (fn [users my-role]
      (let [users-per-row (if (> 992 (:width @viewport)) 2 3)]
        [:div.container-fluid
         (doall (for [[idx row] (map-indexed vector (partition-all users-per-row users))]
                  ^{:key (str "row." idx)}
                  [:div.row
                   (doall (for [{:keys [username role] :as user} row]
                            ^{:key username}
                            [:div.col-lg-4.col-sm-6.col-md-6
                             [:div.well {:style (when (= @me username) my-user-style)}
                              [project-user user role my-role]]]))]))]))))

(defn delete-project-btn [show?]
  (fn [show?]
    [bs/overlay-trigger
     {:overlay (reagent/as-component [bs/tooltip {:id "tooltip"} "Remove project"])
      :placement "bottom"}
     [bs/button
      {:bsSize "small"
       :onClick #(swap! show? not)}
      [bs/glyphicon {:glyph "remove-sign"}]]]))

(defn leave-project-btn [show?]
  (fn [show?]
    [bs/overlay-trigger
     {:overlay (reagent/as-component [bs/tooltip {:id "tooltip"} "Leave project"])
      :placement "bottom"}
     [bs/button
      {:bsSize "small"
       :onClick #(swap! show? not)}
      [bs/glyphicon {:glyph "hand-right"}]]]))

(defn project-buttons [my-role delete-project-show? leave-project-show?]
  (fn [my-role delete-project-show? leave-project-show?]
    [bs/button-toolbar
     (when (can-delete-project? @my-role) [delete-project-btn delete-project-show?])
     [leave-project-btn leave-project-show?]]))

(defn add-user-button [add-user-show?]
  (fn [add-user-show?]
    [bs/button
     {:onClick #(swap! add-user-show? not)}
     "Add User"]))

(defn project-header [project]
  (let [creator (re-frame/subscribe [:active-project-creator])]
    (fn [{:keys [name description created]}]
      [:div.container-fluid
       [:div.row
        [:div.col-lg-8 {:style {:font-size "32px"}} name]
        [:div.col-lg-4 {:style {:text-align "bottom"}}
         [:p.text-right
          "Created by " [:strong @creator] " on " [:span.text-muted (human-time created)]]]]
       [:div.row [:div.col-lg-12 [:p.text-muted description]]]])))

(defn project-panel []
  (let [active-project (re-frame/subscribe [:active-project])
        delete-project-show? (reagent/atom false)
        leave-project-show? (reagent/atom false)
        add-user-show? (reagent/atom false)
        my-role (re-frame/subscribe [:active-project-role])
        me (re-frame/subscribe [:me :username])]
    (fn []
      (let [{:keys [name users] :as project} @active-project]
        [:div.container
         [delete-project-modal name delete-project-show?]
         [leave-project-modal name leave-project-show?]
         [add-user-modal project add-user-show?]
         [:div.container-fluid
          [:div.row [project-header project]]
          [:div.row [:div.col-lg-12 [:hr]]]
          [:div.row
           [:div.col-lg-6
            [:div [add-user-button add-user-show?]]]
           [:div.col-lg-6
            [:div.pull-right [project-buttons my-role delete-project-show? leave-project-show?]]]]
          [:div-row {:style {:margin "20px"}}
           [:div.text [:h4 "Users working in " [:span.text-muted name]]]]
          [:div.row {:style {:height "10px"}}]
          [:div.row [project-users users @my-role]]]]))))
