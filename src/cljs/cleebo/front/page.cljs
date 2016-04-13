(ns cleebo.front.page
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [react-bootstrap.components :as bs]
            [cleebo.routes :refer [nav!]]
            [taoensso.timbre :as timbre]))

(defn submit-project []
  (timbre/debug "A!"))

(defn new-project-btn []
  (let [open? (reagent/atom false)]
    (fn []
      [:div
       [bs/collapse
        {:in @open?}
        [:div
         [bs/well
          [:form
           [bs/input
            {:type "text"
             :label "Project Name"}]
           [bs/input
            {:type "text"
             :label "Add colleagues"}]]]]]
       [bs/button
        {:onClick #(if-not @open? (reset! open? true) (submit-project))
         :class "pull-right"}
        (if-not @open? "New project" "Submit project")]])))

(defn no-projects []
  [:div
   [:p "You don't have current projects. Start one right now."]
   [new-project-btn]])

(defn front-panel []
  [:div.container-fluid
   [:div.col-lg-2]
   [:div.col-lg-8
    [:div;.text-center
     [bs/jumbotron
      [:h2 "Projects"]
      [no-projects]]]]
   [:div.col-lg-2]])




