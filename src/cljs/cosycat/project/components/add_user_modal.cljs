(ns cosycat.project.components.add-user-modal
  (:require [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [react-bootstrap.components :as bs]
            [cosycat.components :refer [user-profile-component]]
            [cosycat.roles :refer [project-user-roles]]
            [cosycat.autosuggest :refer [suggest-users]]
            [cosycat.utils :refer [human-time wrap-key]]
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
    [bs/well
     (if @selected-user-atom
       [selected-user-component selected-user-atom current-selection-atom]
       [user-profile-component (or @current-selection-atom default-user)
        project-user-roles
        :editable? false :displayable? false])]))

(defn add-user-component [& {:keys [remove-project-users] :or {remove-project-users true}}]
  (let [current-selection-atom (reagent/atom nil)
        selected-user-atom (reagent/atom nil)]
    (fn []
      (let []
        [:div.container-fluid
         [:div.row
          [:div.col-lg-7
           [display-user selected-user-atom current-selection-atom]]
          [:div.col-lg-5
           [:div.container-fluid
            [:div.row
             [suggest-users
              {:class "form-control form-control-no-border"
               :remove-project-users remove-project-users
               :on-change #(reset! current-selection-atom (get (zipmap (map :username %2) %2) %)) ;WIP
               :onKeyPress #(when (= 13 (.-charCode %))
                              (let [u (reset! selected-user-atom @current-selection-atom)]
                                (.log js/console "selected" @selected-user-atom)))
               :onKeyDown #(.stopPropagation %)}]]]]]]))))

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
