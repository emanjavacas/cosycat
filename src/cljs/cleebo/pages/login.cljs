(ns cleebo.pages.login
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [re-com.core :as re-com]
            [ajax.core :refer [POST GET ajax-request]]
            [taoensso.timbre :as timbre]))

(declare login-form join-form)

(def tabs-def 
  [{:id :login :label "Login" :say-this "Login with your account"}
   {:id :join :label "Join" :say-this "Create a new account"}])

(defn dispatch-panel [id]
  (case id
    :login [login-form]
    :join  [join-form]))

(defn by-id [id]
  (.getElementById js/document id))

(defn join []
  (let [name (re-frame/subscribe [:name])
        new-name (.-value (by-id "join-first"))]
    (re-frame/dispatch [:new-name new-name])))

(defn login-error [{:keys [status status-text failure] :as response}]
  (timbre/debug response)
  (timbre/debug "Error [" status-text "] with key [" status "] happened. Reason " failure))

(defn login-success [{:keys [message] :as response}]
  (timbre/debug "Got: " response)
  (timbre/debug "Got: " message))

(defn login []
  (let [user-id (.-value (by-id "login-username"))]
    (POST "/login"
          {:params {:user-id user-id}
           :handler login-success
           :error-handler login-error
           :format :json
           :response-format :raw
           :keywords? true})))

(defn login-form []
  [:div.panel
   [:div.panel-body
    [:form.form-horizontal {:id :login}
     [:div.input-group {:style {:margin-bottom "25px"}}
      [:span.input-group-addon
       [:i [re-com/md-icon-button :size :smaller :md-icon-name "zmdi-account-circle"]]]
      [:input.form-control {:id :login-username :type "text" :placeholder "Username/Email"}]]
     [:div.input-group {:style {:margin-bottom "25px"}}
      [:span.input-group-addon
       [:i [re-com/md-icon-button :size :smaller :md-icon-name "zmdi-key"]]]
      [:input.form-control {:id :login-password :type "password" :placeholder "Password"}]]
     [:div.form-group.pull-right
      [:button.btn.btn-secondary
       {:type "button" :style {:margin-right "15px"} :on-click login} "Login"]]]]
      [:div.pull-right {:style {:margin-right "14px" :font-size "11px"}}
       [:a {:href "forgot"} "forgot password?"]]])

(defn join-form []
  [:div.panel
   [:div.panel-body
    [:form.form-horizontal {:id :join}
     [:div.input-group {:style {:margin-bottom "25px"}}
      [:span.input-group-addon
       [:i [re-com/md-icon-button :size :smaller :md-icon-name "zmdi-account-circle"]]]
      [:input.form-control {:id :join-first :type "text" :placeholder "First name"}]]
     [:div.input-group {:style {:margin-bottom "25px"}}
      [:span.input-group-addon
       [:i [re-com/md-icon-button :size :smaller :md-icon-name "zmdi-account-circle"]]]
      [:input.form-control {:id :join-last :type "text" :placeholder "Last name"}]]
     [:div.input-group {:style {:margin-bottom "25px"}}
      [:span.input-group-addon
       [:i [re-com/md-icon-button :size :smaller :md-icon-name "zmdi-email"]]]
      [:input.form-control {:id :join-email :type "text" :placeholder "Email"}]]
     [:div.input-group {:style {:margin-bottom "25px"}}
      [:span.input-group-addon
       [:i [re-com/md-icon-button :size :smaller :md-icon-name "zmdi-key"]]]
      [:input.form-control {:id :join-password :type "password" :placeholder "Password"}]]
     [:div.form-group.pull-right
      [:button.btn.btn-secondary
       {:on-click join :type "button" :style {:margin-right "15px"}} "Join"]]]]])

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
             [re-com/horizontal-tabs
              :tabs tabs-def
              :model selected?
              :on-change re-select]]
            (dispatch-panel @selected?)]]
          [:br]]]]])))
