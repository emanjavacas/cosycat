(ns cosycat.views.login
  (:require [cosycat.views.layout :refer [base]]))

(declare tabs)

(defn alert-error [title]
  [:div.alert.alert-danger
    {:style {:border-right "none"
             :color "#333"
             :background-color "rgba(255, 0, 0, 0.1)"
             :padding "7px"
             :border-left "4px solid rgba(255, 0, 0, 0.8);"
             :border-top "none"
             :border-radius "0px"
             :border-bottom "none"}}
   title])

(defn alert-success [title]
  [:div.alert.alert-success
    {:style {:border-right "none"
             :color "#333"
             :background-color "rgba(60, 118, 61, 0.1);"
             :padding "7px"
             :border-left "4px solid rgba(60, 118, 61, 0.8);"
             :border-top "none"
             :border-radius "0px"
             :border-bottom "none"}}
   title])

(defn login-page [& {:keys [csrf error-msg success-msg]}]
  (base
   {:left  [:p.lead "Please login to access this resource"]
    :right (tabs csrf error-msg success-msg)
    :logged? false}))

(defn tabs [csrf & [error-msg success-msg]]
  [:div.panel.with-nav-tabs.panel-default
   [:div.panel-heading
    [:ul.nav.nav-tabs
     [:li.active [:a {:href "#login"  :data-toggle "tab"} "Login"]]
     [:li        [:a {:href "#signup" :data-toggle "tab"} "Join"]]]]
   [:div.panel-body
    [:div.tab-content
     [:div#login.tab-pane.fade.in.active ;login
      [:form.form-horizontal {:action "/login" :method :post}
       [:input {:style "display:none;" :name "csrf" :value csrf}]
       [:div.input-group {:style "margin-bottom: 25px"}
        [:span.input-group-addon [:i.glyphicon.glyphicon-user]]
        [:input.form-control
         {:placeholder "username or email"
          :required true
          :value ""
          :name "username"
          :type "text"}]]
       [:div.input-group
        {:style "margin-bottom: 25px"}
        [:span.input-group-addon [:i.glyphicon.glyphicon-lock]]
        [:input.form-control
         {:placeholder "password"
          :required true
          :value ""
          :name "password"
          :type "password"}]]
       [:div.form-group
        {:style "margin-top:10px"}
        [:div {:style "text-align: right;"}
         [:div.col-sm-12.controls
          [:div.row
           [:div.col-md-8 {:style "text-align: left;"}
            (if error-msg (alert-error error-msg)
                (if success-msg (alert-success success-msg) ""))]
           [:div.col-md-4
            [:button.btn-login.btn.btn-success {:type "submit"} "Login"]]]]
         [:br]
         [:div {:style "font-size: 12px; padding: 25px 10px 0 0;"}
          [:a {:href "#"} "forgot password?"]]]]]]
     [:div#signup.tab-pane              ;signup
      [:form.form-horizontal {:action "/signup" :method :post}
       [:input {:style "display:none;" :name "csrf" :value csrf}]
       [:div.input-group {:style "margin-bottom: 25px"}
        [:span.input-group-addon [:i.glyphicon.glyphicon-user]]
        [:input.form-control
         {:placeholder "public username"
          :required true
          :value ""
          :name "username"
          :type "text"}]]
       [:div.input-group {:style "margin-bottom: 25px"}
        [:span.input-group-addon [:i.glyphicon.glyphicon-user]]
        [:input.form-control
         {:placeholder "first name"
          :required true
          :value ""
          :name "firstname"
          :type "text"}]]
       [:div.input-group {:style "margin-bottom: 25px"}
        [:span.input-group-addon [:i.glyphicon.glyphicon-user]]
        [:input.form-control
         {:placeholder "last name"
          :required true
          :value ""
          :name "lastname"
          :type "text"}]]
       [:div.input-group {:style "margin-bottom: 25px"}
        [:span.input-group-addon [:i.glyphicon.glyphicon-envelope]]
        [:input.form-control
         {:placeholder "email"
          :required true
          :value ""
          :name "email"
          :type "email"}]]
       [:div.input-group
        {:style "margin-bottom: 25px"}
        [:span.input-group-addon [:i.glyphicon.glyphicon-lock]]
        [:input.form-control
         {:placeholder "password"
          :required true
          :value ""
          :name "password"
          :type "password"}]]
       [:div.input-group
        {:style "margin-bottom: 25px"}
        [:span.input-group-addon [:i.glyphicon.glyphicon-lock]]
        [:input.form-control
         {:placeholder "repeat password"
          :required true
          :value ""
          :name "repeatpassword"
          :type "password"}]]
       [:div.form-group
        {:style "margin-top:10px"}
        [:div.pull-right
         [:div.col-sm-12.controls
          [:button.btn-login.btn.btn-success {:type "submit"} "Join"]]]]]]]]])
