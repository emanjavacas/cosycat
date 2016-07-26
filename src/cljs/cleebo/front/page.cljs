(ns cleebo.front.page
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [react-bootstrap.components :as bs]
            [cleebo.routes :refer [nav!]]
            [cleebo.components :refer [throbbing-panel]]
            [cleebo.front.components.new-project-panel :refer [new-project-btn]]
            [cleebo.front.components.projects-panel :refer [projects-panel]]
            [taoensso.timbre :as timbre]))

(defn no-projects [username]
  (fn [username]
    [:div
     [:p "You don't have current projects. Start one right now."]]))

(defn front-panel []
  (let [projects (re-frame/subscribe [:projects])
        username (re-frame/subscribe [:me :username])]
    (fn []
      [:div.container-fluid
       [:div.row
        [:div.col-lg-1]
        [:div.col-lg-10
         [bs/jumbotron
          [:h2#projects {:style {:padding-bottom "30px"}} "Projects"]
          (if (zero? (count @projects)) ;default project
            [:div [no-projects username] [new-project-btn]]
            [:div [projects-panel projects] [new-project-btn]])]]
        [:div.col-lg-1]]])))
