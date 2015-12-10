(ns cleebo.pages.login
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [re-com.core :as re-com]
            [taoensso.sente :as sente]))

(declare login-form join-form)

(def tabs-def 
  [{:id :login :label "Login" :say-this "Login with your account"}
   {:id :join :label "Join" :say-this "Create a new account"}])

(defn dispatch-panel [id]
  (case id
    :login [login-form]
    :join  [join-form]))

(defn login [])

(defn join  [])

(defn login-form []
  [:div.panel
   [:div.panel-body
    [:form.form-horizontal {:id :login}
     [:div.input-group {:style {:margin-bottom "25px"}}
      [:span.input-group-addon
       [:i [re-com/md-icon-button :size :smaller :md-icon-name "zmdi-account-circle"]]]
      [:input.form-control {:id :login-username :type "text" :placeholder "Username or Email"}]]
     [:div.input-group {:style {:margin-bottom "25px"}}
      [:span.input-group-addon
       [:i [re-com/md-icon-button :size :smaller :md-icon-name "zmdi-key"]]]
      [:input.form-control {:id :login-password :type "password" :placeholder "Password"}]]
     [:div.form-group.pull-right
      [re-com/button :label "Login" :style {:margin-right "15px"}
       :on-click login]]]]
      [:div.pull-right {:style {:margin-right "14px" :font-size "11px"}}
       [:a {:href "forgot"} "forgot password?"]]])

(defn join-form []
  [:div.panel
   [:div.panel-body
    [:form.form-horizontal {:id :login}
     [:div.input-group {:style {:margin-bottom "25px"}}
      [:span.input-group-addon
       [:i [re-com/md-icon-button :size :smaller :md-icon-name "zmdi-account-circle"]]]
      [:input.form-control {:id :login-username :type "text" :placeholder "First name"}]]
     [:div.input-group {:style {:margin-bottom "25px"}}
      [:span.input-group-addon
       [:i [re-com/md-icon-button :size :smaller :md-icon-name "zmdi-account-circle"]]]
      [:input.form-control {:id :login-password :type "text" :placeholder "Last name"}]]
     [:div.input-group {:style {:margin-bottom "25px"}}
      [:span.input-group-addon
       [:i [re-com/md-icon-button :size :smaller :md-icon-name "zmdi-email"]]]
      [:input.form-control {:id :login-password :type "text" :placeholder "Email"}]]
     [:div.input-group {:style {:margin-bottom "25px"}}
      [:span.input-group-addon
       [:i [re-com/md-icon-button :size :smaller :md-icon-name "zmdi-key"]]]
      [:input.form-control {:id :login-password :type "password" :placeholder "Password"}]]     
     [:div.form-group.pull-right
      [re-com/button :label "Join" :style {:margin-right "15px"}
       :on-click join]]]]])

(defn login-panel []
  (let [selected? (reagent/atom (:id (first tabs-def)))
        re-select (fn [id] (reset! selected? id))]
    (fn []
      [re-com/v-box :margin "45px"
       :children
       [[re-com/h-box :gap "1"
         :children
         [[:br]
          [re-com/v-box :width "350px"
           :children 
           [[re-com/box :child 
             [re-com/horizontal-tabs :style {:align "center"}
              :tabs tabs-def
              :model selected?
              :on-change re-select]]
            (dispatch-panel @selected?)]]
          [:br]]]]])))
