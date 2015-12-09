(ns cleebo.core
    (:require [reagent.core :as reagent]
              [re-frame.core :as re-frame]
              [re-com.core :as re-com]
              [cleebo.handlers]
              [cleebo.subs]
              [cleebo.routes :as routes]
              [cleebo.pages.home :refer [home-panel]]
              [cleebo.pages.login :refer [login-panel]]              
              [cleebo.pages.annotation :refer [about-panel]]))

(defmulti panels identity)
(defmethod panels :home-panel [] [home-panel])
(defmethod panels :login-panel [] [login-panel])
(defmethod panels :about-panel [] [about-panel])
(defmethod panels :default [] [:div])

(defn navlink [uri label target-panel collapsed?]
  (let [active-panel (re-frame/subscribe [:active-panel])]
    [:li {:class (when (= @active-panel target-panel) "active")}
     [:a {:href uri
          :on-click #(reset! collapsed? true)}
      label]]))

(defn navbar []
  (let [collapsed? (reagent/atom true)]
    (fn []
      [:nav.navbar.navbar-default.navbar-fixed-top
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
         [:a.navbar-brand {:href "#/"} "Cleebo"]]
        [:div.navbar-collapse.collapse
         (when-not @collapsed? {:class "in"})
         [:ul.nav.navbar-nav.navbar-right
          [navlink "#/login" "login/join" :login-panel collapsed?]
          [navlink "#/about" "about" :about-panel collapsed?]]]]])))

(defn main-panel []
  (let [active-panel (re-frame/subscribe [:active-panel])]
    (fn []
      [re-com/v-box
       :height "100%"
       :gap "55px"
       :children 
       [[navbar]
        (panels @active-panel)]])))

(defn mount-root []
  (reagent/render [main-panel] (.getElementById js/document "app")))

(defn ^:export init [] 
  (routes/app-routes)
  (re-frame/dispatch-sync [:initialize-db])
  (mount-root))

