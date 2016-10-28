(ns cosycat.project.components.add-user-modal
  (:require [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [react-bootstrap.components :as bs]
            [cosycat.components :refer [user-profile-component]]
            [cosycat.roles :refer [project-user-roles]]
            [cosycat.autosuggest :refer [suggest-users]]
            [taoensso.timbre :as timbre]))

(defn remove-project-users [users project-users]
  (remove #(contains? (apply hash-set (map :username project-users)) (:username %)) users))

(defn selected-user-component [selected-user-atom current-selection-atom]
  (fn [selected-user-atom current-selection-atom]
    [user-profile-component @selected-user-atom
     project-user-roles
     :editable? true
     :on-dismiss #(do (reset! current-selection-atom nil) (reset! selected-user-atom nil))
     :on-submit (fn [user role]
                  (re-frame/dispatch
                   [:project-add-user
                    {:user {:username (:username user) :role role}}])
                  (re-frame/dispatch [:add-user @selected-user-atom]) ;WIP
                  (re-frame/dispatch [:close-modal :add-user])
                  (reset! selected-user-atom nil))]))

(defn display-user [selected-user-atom current-selection-atom]
  (fn [selected-user-atom current-selection-atom]
    [:div
     (if @selected-user-atom
       [bs/well [selected-user-component selected-user-atom current-selection-atom]]
       (when @current-selection-atom
         [bs/well
          [user-profile-component @current-selection-atom 
           project-user-roles
           :editable? false :displayable? false]]))]))

(defn find-user-by-name [username users]
  (some #(when (= username (:username %)) %) users))

(defn add-user-component [& {:keys [remove-project-users] :or {remove-project-users true}}]
  (let [current-selection-atom (reagent/atom nil)
        selected-user-atom (reagent/atom nil)]
    (fn []
      (let []
        [:div.container-fluid
         [:div.row [display-user selected-user-atom current-selection-atom]]
         [:div.row
          [suggest-users
           {:class "form-control form-control-no-border"
            :placeholder "Search for user (username, email, etc.)"
            :remove-project-users remove-project-users
            :on-change #(reset! current-selection-atom (find-user-by-name % %2))
            :onKeyDown #(.stopPropagation %)
            :onKeyPress #(when (= 13 (.-charCode %))
                           (reset! selected-user-atom @current-selection-atom))}]]]))))

(defn add-user-modal [project]
  (let [show? (re-frame/subscribe [:modals :add-user])]
    (fn [project]
      [bs/modal
       {:show @show?
        :onHide #(re-frame/dispatch [:close-modal :add-user])}
       [bs/modal-header
        {:closeButton true}
        [bs/modal-title "Add user to the project"]]
       [bs/modal-body
        [add-user-component project]]])))
