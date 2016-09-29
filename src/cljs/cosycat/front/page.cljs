(ns cosycat.front.page
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [react-bootstrap.components :as bs]
            [cosycat.routes :refer [nav!]]
            [cosycat.components :refer [throbbing-panel user-attributes]]
            [cosycat.front.components.new-project-panel :refer [new-project-btn]]
            [cosycat.front.components.projects-panel :refer [projects-panel]]
            [taoensso.timbre :as timbre]))

(defn no-projects []
  [:div [:p "You don't have current projects. Start one right now."]])

(defn projects-col []
  (let [projects (re-frame/subscribe [:projects])
        open? (reagent/atom false)]
    (fn []
      (if-not @open?
        [:div [:h3#projects {:style {:padding-bottom "30px"}} "Projects"]
         (if (zero? (count @projects))
           [:div [no-projects] [new-project-btn open?]]
           [:div [projects-panel projects] [new-project-btn open?]])]
        [:div [new-project-btn open?]]))))

(defn user-col []
  (let [user (re-frame/subscribe [:me])]
    (fn []
      (let [{{href :href} :avatar
             firstname :firstname lastname :lastname username :username
             last-active :last-active created :created
             email :email :as user} @user]
        [:div.container-fluid
         [:div.row {:style {:height "20px"}}]
         [:div.row.text-center
          [:img.img-responsive.img-rounded {:src href :style {:width "75%"}}]]
         [:div.row
          [:h3 (str firstname " " lastname)]
          [:h4.text-muted username]]
         [:div.row {:style {:margin-top "30px"}}
          [:div.col-lg-8 [:hr]]
          [:div.col-lg-4 {:style {:line-height "3em"}}
           [:span {:style {:cursor "pointer"}}
            [bs/glyphicon {:glyph "pencil"}]]]]
         [:div.row [user-attributes user]]]))))

(defn front-panel []
  [:div.container-fluid
   [:div.row
    [:div.col-lg-1.visible-lg]
    [:div.col-lg-3.col-md-4.col-sm-4 [user-col]]
    [:div.col-lg-7.col-md-8.col-sm-8 [projects-col]]
    [:div.col-lg-1.visible-lg]]])

