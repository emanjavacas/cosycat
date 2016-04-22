(ns cleebo.front.components.new-project-panel
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [react-bootstrap.components :as bs]
            [cleebo.utils :refer [by-id]]
            [cleebo.components :refer [user-selection-component css-transition-group]]
            [cleebo.front.components.include-box :refer [include-box-component]]
            [cleebo.app-utils :refer [invalid-project-name]]
            [goog.string :as gstr]
            [taoensso.timbre :as timbre]))

(defn validate-project-input
  [{:keys [name description users] :as project}]
  (and (not (gstr/endsWith name "Playground"))
       (not (invalid-project-name name))))

(defn submit-project [name desc usernames]
  (let [project {:name name :description desc :usernames usernames}]
    (if-not (validate-project-input project)
      (timbre/debug "invalid project name:" name)
      (re-frame/dispatch [:new-project project]))))

(defn label-component [title]
  [:div.row
   [:div.text-muted.pull-right
    {:style {:padding-right "15px" :padding-top "15px"}}
    [:label title]]])

(defn spacer []
  [:div.row {:style {:height "35px"}}])

(defn new-project-form [selected-users]
  (let [users (re-frame/subscribe [:session :users])]
    (fn [selected-users]
      [bs/well
       [:div.container-fluid
        ;; project name
        [:div.row
         {:style {:padding  "0 15px 0 15px"}}
         [:div.input-group
          [:span.input-group-addon "@"]
          [:input.form-control
           {:type "text"
            :id "name-input"
            :placeholder "Insert a beautiful name"}]]]
        [:div.row
         {:style {:padding-right "25px"}}
         [label-component "Project Name"]]
        [spacer]
        ;; add description
        [:div.row
         {:style {:padding "0 15px 0 15px"}}
         [:textarea.form-control
          {:id "desc-input"
           :placeholder "Write a nice description about your project. Seriously."
           :rows "5"
           :style {:resize "vertical"}}]]
        [:div.row
         {:style {:padding-right "25px"}}
         [label-component "Add a Description"]]
        [spacer]
        ;; add users
        (when-not (empty? @users)
          [:div.row
           [include-box-component
            {:model @users
             :on-select #(reset! selected-users %)
             :child-component user-selection-component}]])
        (when-not (empty? @users)
          [:div.row
           {:style {:padding-right "25px"}}
           [label-component "Add Users"]])]])))

(defn new-project-btn []
  (let [open? (reagent/atom false)
        selected-users (reagent/atom #{})] 
    (fn []
      [:div
       [css-transition-group
        {:transition-name "notification"
         :transition-enter-timeout 0
         :transition-leave-timeout 0}
        (when @open? [new-project-form selected-users])]
       [bs/button-toolbar
        {:class "pull-right"}
        [bs/button
         {:onClick #(if-not @open?
                      (reset! open? true)
                      (let [name (by-id "name-input")
                            desc (by-id "desc-input")]
                        (submit-project name desc (map :username @selected-users))))
          :bsStyle (if-not @open? "info" "success")}
         (if-not @open? "New project" "Submit project")]
        (when @open?
          [bs/button
           {:onClick #(reset! open? false)
            :bsStyle "success"}
           "Close"])]])))
