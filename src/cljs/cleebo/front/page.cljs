(ns cleebo.front.page
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [react-bootstrap.components :as bs]
            [cleebo.routes :refer [nav!]]
            [cleebo.app-utils :refer [default-project-name]]
            [cleebo.components :refer [throbbing-panel]]
            [cleebo.front.components.new-project-panel :refer [new-project-btn]]
            [cleebo.front.components.projects-panel :refer [projects-panel]]
            [taoensso.timbre :as timbre]))

(defn no-projects [username]
  [:div
   [:p "You don't have current projects. Start one right now."]
   [:p.text-mute
    {:style {:font-size "15px"}}
    "... or just "
    [:a {:style {:cursor "pointer"}
         :on-click #(nav! (str "/project/" (default-project-name @username)))}
     "try it"]]])

(defn my-throbbing-panel []
  [:div.container-fluid
   [:div.row.text-center
    {:style {:height "250px"}}
    [throbbing-panel :css-class "loader-ticks"]]
   [:div.row.text-center
    [:h2.text-muted "Loading Database"]]])

(defn front-panel []
  (let [projects (re-frame/subscribe [:session :user-info :projects])
        username (re-frame/subscribe [:session :user-info :username])
        throbbing? (re-frame/subscribe [:throbbing? :front-panel])]
    (fn []
      [:div.container-fluid
       [:div.col-lg-1]
       [:div.col-lg-10
        [:div
         (if @throbbing?
           [my-throbbing-panel]
           [bs/jumbotron
            [:h2
             {:style {:padding-bottom "50px"}}
             "Projects"]
            (if (= 1 (count @projects))
              [:div [no-projects username] [new-project-btn]]
              [:div [projects-panel projects] [new-project-btn]])])]]
       [:div.col-lg-1]])))
