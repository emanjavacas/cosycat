(ns cleebo.layout
  (:require [selmer.parser :as parser]
            [hiccup.core :refer [html]]))

(parser/set-resource-path! (clojure.java.io/resource "templates"))

(defn home-page [{:keys [csrf]}]
  (parser/render-file "index.html" {:csrf csrf}))

(defn landing-page [{:keys [csrf]}]
  (parser/render-file "landing.html" {:csrf csrf}))

(defn error-page [error-details]
  (parser/render-file "error.html" error-details))

(declare nav tabs footer)

(def bootstrap-css
  "http://cdnjs.cloudflare.com/ajax/libs/twitter-bootstrap/3.3.5/css/bootstrap.min.css")

(def bootstrap-js
  "https://maxcdn.bootstrapcdn.com/bootstrap/3.3.5/js/bootstrap.min.js")

(def jquery
  "http://code.jquery.com/jquery-1.9.1.min.js")

(defn home-page [{:keys [csrf]}]
  (html
   [:html
    {:lang "en"}
    [:head
     [:meta {:charset "utf-8"}]
     [:link {:href bootstrap-css :rel "stylesheet"}]
     [:link {:href "vendor/css/material-design-iconic-font.min.css" :rel "stylesheet"}]
     [:link {:href "vendor/css/re-com.css", :rel "stylesheet"}]
     [:link
      {:type "text/css" :rel "stylesheet"
       :href "http://fonts.googleapis.com/css?family=Roboto:300,400,500,700,400italic"}]
     [:link
      {:type "text/css" :rel "stylesheet"
       :href "http://fonts.googleapis.com/css?family=Roboto+Condensed:400,300"}]]
    [:body
     [:div#app]
     [:script (str "var csrf =\"" csrf "\";")]
     [:script {:src "js/compiled/app.js"}]
     [:script "cleebo.core.init();"]]]))

(defn error-page
  [{:keys [status title message] :or
    {:csrf "" :status "" :title "Error!" :message "Walk three times around the table!"}}]
  (html
   [:html
    [:head
     [:title "Something bad happened"]
     [:meta
      {:content "text/html; charset=UTF-8", :http-equiv "Content-Type"}]
     [:meta
      {:content "width=device-width, initial-scale=1.0",
       :name "viewport"}]
     [:link {:href bootstrap-css :rel "stylesheet"}]]
    [:body
     [:div.container-fluid
      [:div.row-fluid
       [:div.col-lg-12
        [:div.centering.text-center
         [:div.text-center
          [:h1 [:span.text-danger (str "Error: " status)]]
          [:hr]
          [:h2.without-margin title]
          [:h4.text-danger message]]]]]]]]))

(defn landing-page [{:keys [csrf]}]
  (html
   [:html
  {:lang "en"}
  [:head
   [:meta {:charset "utf-8"}]
   [:script {:type "text/javascript" :src jquery}]
   [:script {:type "text/javascript" :src bootstrap-js}]
   [:link
    {:type "text/css" :rel "stylesheet"
     :href "http://fonts.googleapis.com/css?family=Roboto+Condensed:400,300"}]
   [:link
    {:type "text/css" :rel "stylesheet"
     :href "http://fonts.googleapis.com/css?family=Roboto:300,400,500,700,400italic"}]
   [:link {:href bootstrap-css :rel "stylesheet"}]
   [:link {:href "vendor/css/re-com.css" :rel "stylesheet"}]]
    [:body
     (nav)
     [:div.container-fluid  {:style "margin-top: 80px;"} ;body
      [:div.row-fluid
       [:div.col-lg-7 "Write some cool text about the app"]
       [:div.col-lg-5 (tabs csrf)]]]
     (footer)]]))

(defn nav []
  [:nav.navbar.navbar-default.navbar-fixed-top ;navbar
   [:div.container
    [:div.navbar-header
     [:button.navbar-toggle
      {:aria-controls "navbar"
       :aria-expanded "true"
        :data-toggle "collapse"}
       [:span.sr-only "Toggle Navigation"]
       [:span.icon-bar]
       [:span.icon-bar]
       [:span.icon-bar]]
      [:a.navbar-brand {:href "/"} "Cleebo"]]]])

(defn tabs [csrf]
  [:div.panel.with-nav-tabs.panel-default
   [:div.panel-heading
    [:ul.nav.nav-tabs {:role "tablist"}
     [:li.active [:a {:href "#login" :role "tab" :data-toogle "tab"} "Login"]]
     [:li [:a {:href "#signup" :role "tab" :data-toogle "tab"} "Join"]]]]
   [:div.panel-body
    [:div.tab-content
     [:div#login.tab-pane.active
      [:form#loginform.form-horizontal {:action "/login" :method :post}
       [:input {:style "display:none;" :name "csrf" :value csrf}]
       [:div.input-group {:style "margin-bottom: 25px"}
        [:span.input-group-addon [:i.glyphicon.glyphicon-user]]
        [:input.form-control
         {:placeholder "username or email"
          :value ""
          :name "username"
          :type "text"}]]
       [:div.input-group
        {:style "margin-bottom: 25px"}
        [:span.input-group-addon [:i.glyphicon.glyphicon-lock]]
        [:input.form-control
         {:placeholder "password"
          :value ""
          :name "password"
          :type "password"}]]
       [:div.form-group
        {:style "margin-top:10px"}
        [:div.pull-right
         [:div.col-sm-12.controls
          [:button#btn-login.btn.btn-success {:type "submit"} "Login"]]]]]]
     [:div#signup.tab-pane 
      [:p "asd"]]]]])

(defn footer []
  [:footer.nav.navbar.navbar-inverse.navbar-fixed-bottom ;footer
   {:style "background-color:#2a2730;color:#99979c;"}
   [:div.rc-v-box.display-flex
    {:style
     "align-items: baseline; -webkit-flex-flow: column nowrap; -webkit-flex: 0 0 80px; -webkit-justify-content: flex-start; flex: 0 0 80px; justify-content: flex-start; -webkit-align-items: baseline; flex-flow: column nowrap; margin: 0 15px 0 15px;"}
    [:br]
    [:div.rc-v-box.display-flex
     {:style
      "align-items:stretch;-webkit-flex-flow:column nowrap;-webkit-flex:none;-webkit-justify-content:flex-start;flex:none;justify-content:flex-start;-webkit-align-items:stretch;flex-flow:column nowrap;margin:0 0 0 25px;"}
     [:div.rc-h-box.display-flex
      {:style
       "-webkit-flex-flow:row nowrap;flex-flow:row nowrap;-webkit-flex:none;flex:none;-webkit-justify-content:flex-start;justify-content:flex-start;-webkit-align-items:stretch;align-items:stretch;"}
      [:li
       [:a
        {:style "color:white;font-size:13px;",
         :href "http://www.github.com/emanjavacas/cleebo"}
        "GitHub"]]
      [:div.rc-gap {:style "-webkit-flex:0 0 25px;flex:0 0 25px;width:25px;"} " "]
      [:li
       [:a
        {:style "color:white;font-size:13px;",
         :href "https://www.uantwerpen.be/en/projects/mind-bending-grammars/"}
        "MindBendingGrammars"]]
      [:div.rc-gap {:style "-webkit-flex:0 0 25px;flex:0 0 25px;width:25px;"} " "]
      [:li [:a {:style "color:white;font-size:13px;", :href "http://erc.europa.eu"} "ERC"]]]
     [:div.rc-gap {:style "-webkit-flex:0 0 5px;flex:0 0 5px;height:5px;"} " "]
     [:span {:style "flex:none;width:450px;min-width:450px;"}
      [:p "Powered by ClojureScript and Reagent"]]]
     [:br]]])
