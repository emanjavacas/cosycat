(ns cleebo.project.page
  (:require [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [react-bootstrap.components :as bs]
            [cleebo.project.components.delete-project-modal :refer [delete-project-modal]]
            [cleebo.components :refer [user-profile-component]]
            [cleebo.roles :refer [project-user-roles]]
            [cleebo.utils :refer [human-time]]
            [cleebo.app-utils :refer [ceil]]
            [taoensso.timbre :as timbre]))

(def users-per-row 3)

(defn can-edit-role? [my-role target-role]
  (cond (some #{target-role} ["project-lead" "creator"]) false
        (some #{my-role} ["guest" "user"]) false
        :else true))

(defn can-remove-project? [my-role]
  (not= my-role "guest"))

(defn project-user [{:keys [username]} project-role my-role]
  (let [user (re-frame/subscribe [:user username])]
    (fn [{:keys [username]}]
      [user-profile-component @user project-user-roles
       :role project-role
       ;; :on-submit TODO: send edit to project
       :displayable? true
       :editable? (can-edit-role? my-role project-role)])))

(defn project-users [users my-role]
  (let [me (re-frame/subscribe [:me :username])]
    (fn [users my-role]
      [:div (doall (for [row (partition-all users-per-row users)
                         {:keys [username role] :as user} row]
                     ^{:key username}
                     [:div.col-md-12
                      {:class (str "col-lg-" (int (ceil (/ 12 users-per-row))))}
                      [:div.well
                       {:style (when (= @me username)
                                 {:border "1px solid #d1e8f1"
                                  :background-color "#eff7fa"})}
                       [project-user user role my-role]]]))])))

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
        me (re-frame/subscribe [:me :username])]
    (fn []
      (let [{:keys [users created description name session]} @active-project
            creator (-> (filter #(= "creator" (:role %)) users) first :username)
            my-role (->> users (filter #(= @me (:username %))) first :role)]
        [:div.container-fluid         
         [:div.row
          [project-header name my-role]
          [:hr]
          [:div [key-val-span "description" description]]
          [:div [key-val-span "created" (human-time created)]]
          [:div [key-val-span "creator" creator]]]
         [:div-row {:style {:margin "20px"}}
          [:div.text
           {:style {:margin "0 -15px"}}
           [:h4 "Users working in " [:span.text-muted name]]]]
         [:div.row [project-users users my-role]]]))))

(defn project-panel []
  [:div.container
   [project-description]])
