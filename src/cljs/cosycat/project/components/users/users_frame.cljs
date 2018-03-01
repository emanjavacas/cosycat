(ns cosycat.project.components.users.users-frame
  (:require [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [react-bootstrap.components :as bs]
            [cosycat.project.components.users.add-user-modal :refer [add-user-modal]]
            [cosycat.components :refer [user-profile-component]]
            [cosycat.roles :refer [project-user-roles]]
            [cosycat.utils :refer [human-time format]]
            [cosycat.app-utils :refer [ceil]]
            [cosycat.viewport :refer [viewport]]
            [taoensso.timbre :as timbre]))

(def my-user-style {:border "1px solid #d1e8f1" :background-color "#eff7fa"})

(defn get-users-per-row [users]
  (cond
    (< (count users) 3)         3
    (> (:width @viewport) 1185) 4
    :else                       2))

(defn can-edit-role? [my-role target-role]
  (cond (some #{target-role} ["creator"]) false
        (some #{my-role} ["guest" "user"]) false
        :else true))

(defn can-remove-user? [my-role target-role am-i-admin? am-i-target?]
  (or (and am-i-admin? (not am-i-target?))
      (and (some #{my-role}   ["creator" "project-lead"])
           (some #{target-role} ["guest" "user"]))))

(defn can-add-users? [my-role]
  (contains? #{"project-lead" "creator"} my-role))

(defn project-user [{:keys [username]} project-role my-role me]
  (let [user (re-frame/subscribe [:user username])
        am-i-admin? (re-frame/subscribe [:am-i-admin?])]
    (fn [{:keys [username]} project-role my-role]
      (let [am-i-target? (= username me)]
        [user-profile-component @user project-user-roles
         :role project-role
         ;; :on-submit TODO: send edit to project
         :on-submit (fn [{:keys [username]} role]
                      (re-frame/dispatch
                       [:user-role-update {:username username :new-role role}]))
         :displayable? true
         :removable? (can-remove-user? my-role project-role @am-i-admin? am-i-target?)
         :editable? (can-edit-role? my-role project-role)]))))

(defn project-users [users my-role]
  (let [me (re-frame/subscribe [:me :username])]
    (fn [users my-role]
      (let [users-per-row (get-users-per-row users)]
        [:div.container-fluid
         (doall (for [[idx row] (map-indexed vector (partition-all users-per-row users))]
                  ^{:key (str "row." idx)}
                  [:div.row
                   (doall (for [{:keys [username role] :as user} row]
                            ^{:key username}
                            [:div
                             {:class (format "col-lg-%d col-sm-6 col-md-6" (/ 12 users-per-row))}
                             [:div.well {:style (when (= @me username) my-user-style)}
                              [project-user user role my-role @me]]]))]))]))))

(defn add-user-button []
  (let [show? (re-frame/subscribe [:modals :add-user])]
    (fn []
      [bs/button
       {:onClick #(re-frame/dispatch [(if @show? :close-modal :open-modal) [:add-user]])}
       "Add User"])))

(defn users-frame []
  (let [active-project (re-frame/subscribe [:active-project])
        my-role (re-frame/subscribe [:active-project-role])]
    (fn []
      (let [{:keys [name users] :as project} @active-project]
        [:div
         [add-user-modal]
         ;; TODO: remove-user-modal
         [:div.container-fluid
          [:div.row
           [:div.col-lg-12
            (when (can-add-users? @my-role) [:div.pull-right [add-user-button]])]]
          [:div.row {:style {:height "10px"}}]
          [:div.row [project-users users @my-role]]]]))))
