(ns cleebo.core
    (:require [reagent.core :as reagent]
              [re-frame.core :as re-frame]
              [re-com.core :as re-com]
              [cleebo.handlers]
              [cleebo.subs]
              [cleebo.routes :as routes]
              [cleebo.pages.query :refer [query-panel]]))

(defmulti panels identity)
(defmethod panels :query-panel [] [query-panel])
(defmethod panels :default [] [:div])

(defn navlink [uri label target-panel collapsed?]
  (let [active-panel (re-frame/subscribe [:active-panel])]
    [:li {:class (when (= @active-panel target-panel) "active")}
     [:a {:href uri
          :on-click #(reset! collapsed? true)}
      label]]))

(defn footerlink [label href]
  (fn []
    [:a {:href href :style {:color "white" :font-size "13px"}} label]))

(defn navbar []
  (let [collapsed? (reagent/atom true)
        name (re-frame/subscribe [:name])]
    (fn []
      [:nav.navbar.navbar-default.navbar-fixed-top
       {:style {:background-color "#2a2730" :color "#99979c" :min-height "25px !important"}}
       [:div.container
        [:div.navbar-header
         [:button.navbar-toggle
          {:class         (when-not @collapsed? "collapsed")
           :data-toggle   "collapse"
           :aria-expanded @collapsed?
           :aria-controls "navbar"
           :on-click      #(swap! collapsed? not)}
          [:span.sr-only  "Toggle Navigation"]
          [:span.icon-bar]
          [:span.icon-bar]
          [:span.icon-bar]]
         [:a.navbar-brand {:href "#/"} @name]]
        [:div.navbar-collapse.collapse
         [:ul.nav.navbar-nav.navbar-right
          [:li [footerlink "out" "/out"]]]]]])))

(defn sidelink [target href label]
  (let [active (re-frame/subscribe [:active-panel])]
    (fn []
      [:li {:class (when (= active target) "active")}
       [:a {:href href} label]])))

(defn sidebar []
  (fn []
    [:div.col-sm-3.col-md-2.sidebar
     [:ul.nav.nav-sidebar
      {:style {:margin-right "-21px;" :margin-bottom "20px;" :margin-left "-20px;"}}
      [sidelink :query-panel "#/query" "query"]
      [sidelink :query-panel "#/home" "home?"]
      [sidelink :query-panel "#/else" "else"]]]))

(defn main-panel []
  (let [active-panel (re-frame/subscribe [:active-panel])]
    (fn []
      [:div
       [navbar]
       [:div.container-fluid
        [:div.row {:style {:padding-top "50px;"}}
         [sidebar]
         [:div.col-sm-9.col-sm-offset-3.col-md-10.col-md-offset-2.main
          (panels @active-panel)]]]])))

(defn mount-root []
  (reagent/render [main-panel] (.getElementById js/document "app")))

(defn ^:export init [] 
  (routes/app-routes)
  (re-frame/dispatch-sync [:initialize-db])
  (mount-root))

