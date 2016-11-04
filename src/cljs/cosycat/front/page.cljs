(ns cosycat.front.page
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [react-bootstrap.components :as bs]
            [cosycat.routes :refer [nav!]]
            [cosycat.components :refer [throbbing-panel user-attributes]]
            [cosycat.front.components.new-project-panel :refer [new-project-btn new-project-panel]]
            [cosycat.front.components.projects-panel :refer [projects-panel]]
            [cosycat.front.components.edit-user-modal :refer [edit-user-modal]]
            [taoensso.timbre :as timbre]))

(defn projects-column []
  (let [selected-users (reagent/atom {})
        open? (reagent/atom false)]
    (fn []
      [:div.container-fluid
       [:div.row.pad                    ;header
        [:h3#projects {:style {:padding-bottom "30px"}}
         (if @open? "New project" "Projects")]]
       [:div.row.pad                    ;btn
        [new-project-btn open? selected-users]]
       [:div.row {:style {:height "10px"}}]
       [:div.row.pad                    ;panel
        (if @open?
          [new-project-panel selected-users]
          [projects-panel])]])))

(defn user-column []
  (let [user (re-frame/subscribe [:me])
        edit-user-show? (reagent/atom false)]
    (fn []
      (let [{{href :href} :avatar
             firstname :firstname lastname :lastname username :username
             last-active :last-active created :created
             email :email :as user} @user]
        [:div [:div.container-fluid
               [:div.row {:style {:height "20px"}}]
               [:div.row
                [:img.img-responsive.img-rounded.pull-right {:src href :width "80"}]]
               [:div.row.text-right
                {:style {:text-align "right"}}
                [:h3 (str firstname " " lastname)]
                [:h4.text-muted username]]
               [:div.row.text-right {:style {:height "100px"}}]
               [:div.row.text-right
                [:div.col-lg-2 {:style {:line-height "3em"}}
                 [:span {:style {:cursor "pointer"}}
                  [bs/glyphicon {:glyph "pencil" :onClick #(swap! edit-user-show? not)}]]]
                [:div.col-lg-10 [:hr]]]
               [:div.row.text-right
                [user-attributes user :align :right]]]
         [edit-user-modal user edit-user-show?]]))))

(defn front-panel []
  [:div.container-fluid
   [:div.row
    [:div.col-lg-1.visible-lg]
    [:div.col-lg-3.col-md-4.col-sm-4
     {:style {:border-right "1px solid #eeeeee"}}
     [user-column]]
    [:div.col-lg-7.col-md-8.col-sm-8
     [projects-column]]
    [:div.col-lg-1.visible-lg]]])

