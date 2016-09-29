(ns cosycat.project.components.add-user-modal
  (:require [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [react-bootstrap.components :as bs]
            [cosycat.components :refer [user-profile-component]]
            [cosycat.roles :refer [project-user-roles]]
            [cosycat.autosuggest :refer [suggest-users]]
            [cosycat.utils :refer [human-time]]
            [cosycat.app-utils :refer [ceil pending-users]]
            [taoensso.timbre :as timbre]))

(def default-user
  {:username "JohnDoe"
   :firstname "John"
   :lastname "Doe"
   :email "john@doe.com"
   :created 123456789
   :last-active 123456789
   :active false
   :avatar {:href "img/avatars/default.png"}})

(defn remove-project-users [users project-users]
  (remove #(contains? (apply hash-set (map :username project-users)) (:username %)) users))

(defn selected-user-component [selected-user-atom current-selection-atom show?]
  (let [active-project (re-frame/subscribe [:active-project :name])]
    (fn [selected-user-atom current-selection-atom show?]
      [user-profile-component @selected-user-atom
       project-user-roles
       :editable? true
       :on-dismiss #(do (reset! current-selection-atom nil) (reset! selected-user-atom nil))
       :on-submit (fn [user role]
                    (re-frame/dispatch
                     [:project-add-user
                      {:user {:username (:username user) :role role}
                       :project-name @active-project}])
                    (swap! show? not)
                    (reset! selected-user-atom nil))])))

(defn display-user [selected-user-atom current-selection-atom show?]
  (fn [selected-user-atom current-selection-atom show?]
    [bs/well
     (if @selected-user-atom
       [selected-user-component selected-user-atom current-selection-atom show?]
       [user-profile-component (or @current-selection-atom default-user)
        project-user-roles
        :editable? false :displayable? false])]))

(defn add-user-component [project show?]
  (let [current-selection-atom (reagent/atom nil)
        selected-user-atom (reagent/atom nil)
        users (re-frame/subscribe [:users])]
    (fn [{project-users :users} show?]
      (let [eligible-users (remove-project-users @users project-users)
            users-by-username (zipmap (map :username @users) @users)]
        [:div.container-fluid
         [:div.row
          [:div.col-lg-7
           [display-user selected-user-atom current-selection-atom show?]]
          [:div.col-lg-5
           [:div.container-fluid
            [:div.row
             [suggest-users eligible-users
              {:class "form-control form-control-no-border"
               :on-change #(reset! current-selection-atom (get users-by-username %))
               :onKeyDown #(.stopPropagation %)
               :onKeyPress #(when (= 13 (.-charCode %))
                              (reset! selected-user-atom @current-selection-atom))}]]]]]]))))

(defn add-user-modal [project add-user-show?]
  (fn [project add-user-show?]
    [bs/modal
     {:show @add-user-show?
      :onHide #(reset! add-user-show? false)}
     [bs/modal-header
      {:closeButton true}
      [bs/modal-title "Add user to the project"]]
     [bs/modal-body
      [add-user-component project add-user-show?]]]))
