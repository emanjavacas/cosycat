(ns cleebo.views.layout
  (:require [hiccup.core :refer [html]]
            [cleebo.views.imgs :refer [random-img]]))

(declare base nav tabs footer)

(defn landing-page [& {:keys [logged?] :or {:logged? false}}]
  (let [{href :href name :name} (random-img)]
    (base 
     {:left  [:div
              [:h2 "Welcome to the home page. "
               [:span.text-muted "Corpus Linguistics with ECCO & EEBO"]]              
              [:p.lead "Donec ullamcorper nulla non metus auctor fringilla. 
                      Vestibulum id ligula porta felis euismod semper.
                      Praesent commodo cursus magna, vel scelerisque nisl consectetur.
                      Fusce dapibus, tellus ac cursus commodo."]]
      :right [:div.panel.panel-default
              [:div.panel-body {:style "text-align: center;"}
               [:img {:src (str "img/" href) :alt name}]]
              [:div.panel-footer {:style "text-align: center;"} name]]
      :logged? logged?})))

(defn about-page [& {:keys [logged?] :or {:logged? false}}]
  (let [{href :href name :name} (random-img)]
    (base 
     {:left  [:div
              [:h2 "About page. "]
              [:h3 [:span.text-muted "This page was created so&so."]]
              [:p.lead "Some text block"]
              [:p.lead "Followed"]
              [:p.lead "By another"]]
      :right [:div.panel.panel-default
              [:div.panel-body {:style "text-align: center;"}
               [:img {:src (str "img/" href) :alt name}]]
              [:div.panel-footer {:style "text-align: center;"} name]]
      :logged? logged?})))

(defn login-page [& {:keys [csrf]}]
  (base
   {:left  [:p.lead "Please login to access this resource"]
    :right (tabs csrf)
    :logged? false}))

(def bootstrap-css
  "http://cdnjs.cloudflare.com/ajax/libs/twitter-bootstrap/3.3.5/css/bootstrap.min.css")

(def bootstrap-js
  "https://maxcdn.bootstrapcdn.com/bootstrap/3.3.5/js/bootstrap.min.js")

(def jquery
  "http://code.jquery.com/jquery-1.9.1.min.js")

(defn cleebo-page [& {:keys [csrf]}]
  (html
   [:html
    {:lang "en"}
    [:head
     [:meta {:charset "utf-8"}]
     [:link {:href bootstrap-css :rel "stylesheet"}]
     [:link {:href "vendor/css/material-design-iconic-font.min.css" :rel "stylesheet"}]
     [:link {:href "vendor/css/dashboard.css" :rel "stylesheet"}]
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
  [& {:keys [status title message] :or
      {:status "404" :title "Error!" :message "Walk three times around the table!"}}]
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

(defn base [{:keys [left right logged?]}]
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
     (nav logged?)
     [:div.container  {:style "padding: 80px 40px 0 40px;"} ;body
      [:div.row
       [:div.col-md-7 left]
       [:div.col-md-5 right]]]
     (footer)]]))

(defn tabs [csrf]
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
        [:div.pull-right {:style "text-align: right;"}
         [:div.col-sm-12.controls
          [:button.btn-login.btn.btn-success {:type "submit"} "Login"]]
         [:br]
         [:div {:style "font-size: 12px; padding: 25px 10px 0 0;"}
          [:a {:href "#"} "forgot password?"]]]]]]
     [:div#signup.tab-pane              ;signup
      [:form.form-horizontal {:action "/signup" :method :post}
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
       [:div.input-group
        {:style "margin-bottom: 25px"}
        [:span.input-group-addon [:i.glyphicon.glyphicon-lock]]
        [:input.form-control
         {:placeholder "repeat password"
          :value ""
          :name "repeatpassword"
          :type "password"}]]
       [:div.form-group
        {:style "margin-top:10px"}
        [:div.pull-right
         [:div.col-sm-12.controls
          [:button.btn-login.btn.btn-success {:type "submit"} "Join"]]]]]]]]])

(defn nav [& [logged?]]
  [:nav.navbar.navbar-default.navbar-fixed-top ;navbar
   [:div.container
    [:div.navbar-header {:style "padding: 0 0 0 25px"}
     [:button.navbar-toggle
      {:aria-controls "navbar"
       :aria-expanded "true"
        :data-toggle "collapse"}
       [:span.sr-only "Toggle Navigation"]
       [:span.icon-bar]
       [:span.icon-bar]
       [:span.icon-bar]]
      [:a.navbar-brand {:href "/"} "Cleebo"]]
    [:div.navbar-collapse.collapse
     [:ul.nav.navbar-nav.navbar-right {:style "margin-right: 25px;"}
      (if logged? [:li [:a {:href "/logout"} "Logout"]] [:li " "])
      [:li [:a {:href "/about"} "About"]]
      [:li [:a {:href "/cleebo"} "Query"]]]]]])

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
