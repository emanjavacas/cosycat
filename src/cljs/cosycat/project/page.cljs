(ns cosycat.project.page
  (:require [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [react-bootstrap.components :as bs]
            [cosycat.project.components.delete-project-modal :refer [delete-project-modal]]
            [cosycat.project.components.add-user-component :refer [add-user-component]]
            [cosycat.components :refer [user-profile-component]]
            [cosycat.roles :refer [project-user-roles]]
            [cosycat.utils :refer [human-time format]]
            [cosycat.app-utils :refer [ceil]]
            [cosycat.viewport :refer [viewport]]
            [taoensso.timbre :as timbre]))

(def users-per-row 3)

(defn can-edit-role? [my-role target-role]
  (cond (some #{target-role} ["project-lead" "creator"]) false
        (some #{my-role} ["guest" "user"]) false
        :else true))

(defn can-remove-project? [my-role]
  (not= my-role "guest"))

(defn can-add-users? [my-role]
  (contains? #{"project-lead" "creator"} my-role))

(defn project-user [{:keys [username]} project-role my-role]
  (let [user (re-frame/subscribe [:user username])]
    (fn [{:keys [username]} project-role my-role]
      [user-profile-component @user project-user-roles
       :role project-role
       ;; :on-submit TODO: send edit to project
       :displayable? true
       :editable? (can-edit-role? my-role project-role)])))

(def my-user-style
  {:border "1px solid #d1e8f1"
   :background-color "#eff7fa"})

(defn col-class [users-per-row]
  ;format
  "col-lg-6.col-sm-6.col-md-6"
                                        ;(int (ceil (/ 12 users-per-row)))
  )

(defn project-users [users my-role]
  (let [me (re-frame/subscribe [:me :username])]
    (fn [users my-role]
      (let [users-per-row (if (> 992 (:width @viewport)) 2 3)
            users+ (into (vec users) (when (can-add-users? my-role) [add-user-component]))]
        [:div.container
         (doall (for [[idx row] (map-indexed vector (partition-all users-per-row users+))]
                  ^{:key (str "row." idx)}
                  [:div.row
                   (doall (for [{:keys [username role] :as user-or-add-user-component} row]
                            ^{:key (or username "add-user-component")}
                            [:div.col-lg-4.col-sm-6.col-md-6
                             [:div.well {:style (when (= @me username) my-user-style)}
                              (if (and username role)
                                ;; render user component
                                [project-user user-or-add-user-component role my-role]
                                ;; render add user component
                                [user-or-add-user-component users])]]))]))]))))

(defn key-val-span [key val]
  (fn [key val]
    [:span {:style {:font-size "18px"}}
     (str key ": ")
     [:h4.text-muted {:style {:display "inline-block"}}
      val]]))

(defn project-header [name my-role]
  (let [delete-project-modal-show (reagent/atom false)]
    (fn [name my-role]
      [:div.text [:h2 name]
       (when (can-remove-project? my-role)
         [:div.text.pull-right
          [bs/overlay-trigger
           {:overlay (reagent/as-component [bs/tooltip {:id "tooltip"} "Remove project"])
            :placement "right"}
           [bs/button
            {:bsSize "small"
             :onClick #(swap! delete-project-modal-show not)}
            [bs/glyphicon {:glyph "remove-sign"}]]]])
       [delete-project-modal name delete-project-modal-show]])))

(defn project-description []
  (let [active-project (re-frame/subscribe [:active-project])
        creator (re-frame/subscribe [:active-project-creator])
        my-role (re-frame/subscribe [:active-project-role])
        me (re-frame/subscribe [:me :username])]
    (fn []
      (let [{:keys [created description name session users]} @active-project]
        [:div.container-fluid         
         [:div.row
          [project-header name @my-role]
          [:hr]
          [:div [key-val-span "description" description]]
          [:div [key-val-span "created" (human-time created)]]
          [:div [key-val-span "creator" @creator]]]
         [:div-row {:style {:margin "20px"}}
          [:div.text
           {:style {:margin "0 -15px"}}
           [:h4 "Users working in " [:span.text-muted name]]]]
         [:div.row [project-users users @my-role]]]))))

(defn project-panel []
  [:div.container
   [project-description]])
