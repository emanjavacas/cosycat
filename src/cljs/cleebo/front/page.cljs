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
        username (re-frame/subscribe [:me :username])
        open? (reagent/atom false)]
    (fn []
      [:div.container-fluid
       [:div.row
        [:div.col-lg-1]
        [:div.col-lg-10
         [bs/jumbotron
          (if-not @open?
            [:div
             [:h2#projects {:style {:padding-bottom "30px"}} "Projects"]
             (if (zero? (count @projects))
               [:div [no-projects username] [new-project-btn open?]]
               [:div [projects-panel projects] [new-project-btn open?]])]
            [new-project-btn open?])]]
        [:div.col-lg-1]]])))
