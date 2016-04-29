(ns cleebo.front.components.new-project-panel
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [react-bootstrap.components :as bs]
            [cleebo.utils :refer [by-id]]
            [cleebo.roles :refer [project-user-roles]]
            [cleebo.components :refer [user-selection-component css-transition-group]]
            [cleebo.front.components.include-box :refer [include-box-component]]
            [cleebo.app-utils :refer [invalid-project-name]]
            [goog.string :as gstr]
            [taoensso.timbre :as timbre]))

(defn label-component [title]
  [:div.text-muted.pull-right
   {:style {:padding-right "15px" :padding-top "5px"}}
   [:label title]])

(defn spacer []
  [:div.row {:style {:height "35px"}}])

(def name-input-id :new-project-name-input)
(def desc-input-id :new-project-desc-input)

(defn on-input-change [has-error-atom component-id]
  (fn []
    (when @has-error-atom
      (re-frame/dispatch [:drop-error component-id]))))

(defn error-label [has-error-atom]
  (fn [has-error-atom]
    (if @has-error-atom
      [:span.help-block {:style {:color "red"}} (:msg @has-error-atom)]
      [:span.help-block])))

(defn name-input-component [name-input-error]
  (fn [name-input-error]
    [:div.row
     {:style {:padding  "0 15px 0 15px"}}
     [:div.input-group
      {:class (when @name-input-error "has-error")}
      [:span.input-group-addon "@"]
      [:input.form-control
       {:type "text"
        :id "name-input"
        :placeholder "Insert a beautiful name"
        :on-change (on-input-change name-input-error name-input-id)}]]
     [error-label name-input-error]
     [label-component "Project Name"]]))

(defn desc-input-component [desc-input-error]
  (fn [desc-input-error]
    [:div.row
     {:style {:padding "0 15px 0 15px"}}
     [:div.input-group
      {:class (when @desc-input-error "has-error")
       :style {:width "100%"}}
      [:textarea.form-control
       {:id "desc-input"
        :placeholder "Write a nice description about your project. Seriously."
        :rows "5"
        :on-change (on-input-change desc-input-error desc-input-id)
        :style {:resize "vertical"}}]]
     [error-label desc-input-error]
     [label-component "Add a Description"]]))

(defn users-input-component [users selected-users]
  (fn [users selected-users]
    (when-not (empty? @users)
      [:div
       [:div.row
        [include-box-component
         {:model @users
          :on-select #(reset! selected-users %)
          :child-component user-selection-component}]
        [label-component "Add Users"]]])))

(defn move-cursor [dir els]
  (fn [current-position]
    (let [[f top] (case dir
                    :next [inc (dec (count els))]
                    :prev [dec (inc (count els))])]
      (mod (f current-position) top))))

(defn select-role-btn [user]
  (let [current-role (reagent/atom 0)]
    (fn [user]
      (let [[role desc] (nth (seq project-user-roles) @current-role)]
        [:div
         [:div.input-group
          [:span.input-group-btn
           [:button.btn.btn-default
            {:type "button"
             :on-click #(swap! current-role (move-cursor :next project-user-roles))}
            [bs/glyphicon {:glyph "chevron-left"}]]]
          [:span.form-control [:label role]]
          [:span.input-group-btn
           [:button.btn.btn-default
            {:type "button"
             :on-click #(swap! current-role (move-cursor :prev project-user-roles))}
            [bs/glyphicon {:glyph "chevron-right"}]]]]
         [:span desc]]))))

(defn roles-input-component [users selected-users]
  (fn [users selected-users]
    (when-not (empty? @selected-users)
      [:div.row
       [:div.container-fluid
        [bs/list-group
         (let [selected-users-names (apply hash-set (map :username @selected-users))]
           (doall (for [{:keys [username active] :as user} @users
                        :when (contains? selected-users-names username)]
                    ^{:key username}
                    [bs/list-group-item
                     [:div.row
                      [:div.col-lg-6.text-center [user-selection-component user]]
                      [:div.col-lg-6.text-center [select-role-btn user]]]])))]]
       (apply str @selected-users)
       [label-component "Assign Project Roles"]])))

(defn new-project-form [selected-users {:keys [name-input-error desc-input-error]}]
  (let [users (re-frame/subscribe [:session :users])]
    (fn [selected-users {:keys [name-input-error desc-input-error]}]
      [bs/well
       [:div.container-fluid
        [name-input-component name-input-error]
        [spacer]
        [desc-input-component desc-input-error]
        [spacer]
        [users-input-component users selected-users]
        [spacer]
        [roles-input-component users selected-users]]])))

(defn validate-project-input
  [{:keys [name description users] :as project} user-projects]
  (cond
    (contains? (apply hash-set (map :name user-projects)) name)
    [name-input-id "Project already exists!"]
    (gstr/endsWith name "-playground")
    [name-input-id "Project name cannot end with '-playground'"]
    (invalid-project-name name)
    [name-input-id "Project name cannot match [ ^\\W+]"]
    (> 50 (count description))
    [desc-input-id "Type at least 50 characters... please!"]
    :else false))

(defn submit-project [{:keys [name desc usernames user-projects]}]
  (let [project {:name name :description desc :usernames usernames}]
    (timbre/debug @user-projects)
    (if-let [[component-id error-msg] (validate-project-input project @user-projects)]
      (re-frame/dispatch [:register-error component-id {:msg error-msg}])
      (re-frame/dispatch [:new-project project]))))

(defn on-new-project [open? selected-users user-projects & {:keys [input-error-atoms]}]
  (fn []
    (if-not @open?
      (reset! open? true)
      (let [name (by-id "name-input")
            desc (by-id "desc-input")]
        (when-not (some identity (map deref input-error-atoms)) 
          (submit-project
           {:name name
            :description desc
            :usernames (map :username @selected-users)
            :user-projects user-projects}))))))

(defn new-project-btn []
  (let [open? (reagent/atom false)
        name-input-error (re-frame/subscribe [:has-error? name-input-id])
        desc-input-error (re-frame/subscribe [:has-error? desc-input-id])
        user-projects (re-frame/subscribe [:session :user-info :projects])
        selected-users (reagent/atom #{})]
    (fn []
      [:div
       [css-transition-group
        {:transition-name "notification"
         :transition-enter-timeout 0
         :transition-leave-timeout 0}
        (when @open?
          [new-project-form selected-users
           {:name-input-error name-input-error
            :desc-input-error desc-input-error}])]
       [bs/button-toolbar
        {:class "pull-right"}
        [bs/button
         {:onClick
          (on-new-project
           open? selected-users user-projects
           :input-error-atoms [name-input-error desc-input-error])
          :bsStyle (if-not @open? "info" "success")}
         (if-not @open? "New project" "Submit project")]
        (when @open?
          [bs/button
           {:onClick #(reset! open? false)
            :bsStyle "success"}
           "Close"])]])))
