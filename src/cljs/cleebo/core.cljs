(ns cleebo.core
    (:require [reagent.core :as reagent]
              [re-frame.core :as re-frame]
              [re-com.core :as re-com]
              [taoensso.sente :as sente]
              [cleebo.handlers]
              [cleebo.subs]
              [cleebo.routes :as routes]
              [cleebo.pages.home :refer [home-panel]]
              [cleebo.pages.login :refer [login-panel]]              
              [cleebo.pages.about :refer [about-panel]]
              
              [cleebo.pages.login :refer [join]]))

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
  (let [collapsed? (reagent/atom true)
        name (re-frame/subscribe [:name])]
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
         [:a.navbar-brand {:href "#/"} @name]]
        [:div.navbar-collapse.collapse
         (when-not @collapsed? {:class "in"})
         [:ul.nav.navbar-nav.navbar-right
          [navlink "#/login" "login/join" :login-panel collapsed?]
          [navlink "#/about" "about" :about-panel collapsed?]]]]])))

(defn footerlink [label href]
  (fn []
    [:a {:href href :style {:color "white" :font-size "13px"}} label]))

(defn footer []
  [:footer.nav.navbar.navbar-inverse.navbar-fixed-bottom
   {:style {:background-color "#2a2730" :color "#99979c"}}
   [re-com/v-box :align :baseline :size "80px" :margin "0 15px 0 15px" 
    :children
    [[:br]
     [re-com/v-box :margin "0 0 0 25px" :gap "5px"
      :children 
      [[re-com/h-box :gap "25px"
        :children
        [[:li [footerlink "GitHub" "http://www.github.com/emanjavacas/cleebo"]]
         [:li [footerlink "MindBendingGrammars" "https://www.uantwerpen.be/en/projects/mind-bending-grammars/"]]
         [:li [footerlink "ERC" "http://erc.europa.eu"]]]]
       [re-com/p "Powered by ClojureScript and Reagent"]]]
     [:br]]]])

(defn main-panel []
  (let [active-panel (re-frame/subscribe [:active-panel])]
    (fn []
      [re-com/v-box
       :height "100%"
       :gap "55px"
       :children 
       [[navbar]
        (panels @active-panel)
        [footer]]])))

(defn mount-root []
  (reagent/render [main-panel] (.getElementById js/document "app")))

(defn ^:export init [] 
  (routes/app-routes)
  (re-frame/dispatch-sync [:initialize-db])
  (mount-root))

