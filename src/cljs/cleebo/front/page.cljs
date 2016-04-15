(ns cleebo.front.page
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [react-bootstrap.components :as bs]
            [cleebo.routes :refer [nav!]]
            [cleebo.utils :refer [by-id]]
            [cleebo.components :refer [user-thumb]]
            [taoensso.timbre :as timbre]))

(defn validate-project-input [project]
  true)

(defn submit-project []
  (let [name (by-id "name-input")
        desc (by-id "desc-input")
        users (by-id "users-input")
        project {:name name :description desc :users users}]
    (validate-project-input project)
    (re-frame/dispatch [:new-project project])))

(defn include-box [model child-component]
  (fn [model]
    [bs/list-group
     (doall (for [[idx child-data] (map-indexed vector @model)]
              ^{:key (str "box-" idx)}
              [bs/list-group-item
               (reagent/as-component [child-component child-data])]))]))

(defn user-selection-component [child-data]
  (fn [{:keys [username avatar last-active]}]
    [:div.container-fluid
     [:div.row
      [:div.col-sm-8 [:p username]]
      [:div.col-sm-4 [user-thumb username {:height "25px" :width "25px"}]]]]))

(defn include-box-component [model model-component]
  (let [selected (reagent/atom [])]
    (fn [model model-component]
      [:div.container-fluid
       [:div.row
        [:div.col-lg-5
         "Selected users"
         [include-box selected user-selection-component]]
        [:div.col-lg-2]
        [:div.col-lg-5
         "Available users"
         [include-box model user-selection-component]]]])))

(defn new-project-btn []
  (let [open? (reagent/atom false)
        users (re-frame/subscribe [:session :users])]
    (fn []
      [:div
       [bs/collapse
        {:in @open?}
        [:div
         [bs/well
          [:div.container-fluid
           [:div.row
            [:div.input-group
             [:span.input-group-addon "@"]
             [:input.form-control
              {:type "text" :id "name-input" :placeholder "Insert a beautiful name"}]]]
           [:div.row                    ;project name
            [:div.text-muted.pull-right
             {:style {:padding-right "20px"}}
             [:label "Project name"]]]
           [:div.row
            [include-box-component users user-selection-component]]
           [:div.row                    ;add users
            [:div.text-muted.pull-right
             {:style {:padding-right "20px"}}
             [:label"Add users"]]]]]]]
       [bs/button
        {:onClick #(if-not @open? (reset! open? true) (submit-project))
         :bsStyle (if-not @open? "info" "success")
         :class "pull-right"}
        (if-not @open? "New project" "Submit project")]])))

(defn no-projects []
  [:div
   [:p "You don't have current projects. Start one right now."]
   [new-project-btn]])

(defn projects-panel [projects]
  (fn [projects]
    [:div "Projects"]))

(defn front-panel []
  (let [projects (re-frame/subscribe [:session :user-info :projects])]
    (fn []
      [:div.container-fluid
       [:div.col-lg-2]
       [:div.col-lg-8
        [:div;.text-center
         [bs/jumbotron
          [:h2 "Projects"]
          (if (empty? @projects)
            [no-projects]
            [projects-panel projects])]]]
       [:div.col-lg-2]])))
