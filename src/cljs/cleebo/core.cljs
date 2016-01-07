(ns cleebo.core
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [re-com.core :as re-com]
            [cleebo.handlers]
            [cleebo.subs]
            [cleebo.routes :as routes]
            [cleebo.ws :refer [make-ws-ch]]
            [cleebo.pages.query :refer [query-panel]]
            [taoensso.timbre :as timbre]
            [figwheel.client :as figwheel]))

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
  (let [name (re-frame/subscribe [:name])]
    (fn []
      [:nav.navbar.navbar-default.navbar-fixed-top
       {:style {:background-color "#2a2730"
                :color "#99979c"
                :min-height "25px !important"}}
       [:div.container
        [:div.navbar-header
         [:a.navbar-brand {:href "#/"} @name]]
        [:div.navbar-collapse.collapse
         [:ul.nav.navbar-nav.navbar-right
          [:li [re-com/md-icon-button
                :md-icon-name "zmdi-power"
                :style {:margin-top "7px"}
                :size :larger
                :on-click #(.assign js/location "/logout")]]]]]])))

(defn sidelink [target href label]
  (let [active (re-frame/subscribe [:active-panel])]
    (fn []
      [:li {:class (when (= active target) "active")}
       [:a {:href href} label]])))

(defn main-panel []
  (let [active-panel (re-frame/subscribe [:active-panel])]
    (fn []
      [:div
       [navbar]
       [:div.container-fluid
        [:div.row {:style {:padding-top "45px;"}}
         [:div.col-sm-2.col-md-1.sidebar ;sidebar
          [:ul.nav.nav-sidebar
           [sidelink :query-panel "#/query" "query"]
           [sidelink :query-panel "#/home" "home"]
           [sidelink :query-panel "#/settings" "settings"]      
           [sidelink :query-panel "#/updates" "updates"]]]
         [:div.col-sm-10.col-sm-offset-2.col-md-11.col-md-offset-1.main
          (panels @active-panel)]]]])))

(defn mount-root []
  (.log js/console "Called mount-root")
  (reagent/render [#'main-panel] (.getElementById js/document "app")))

(defn set-ws-ch []
  (make-ws-ch
   (str "ws://" (.-host js/location) "/ws")
   #(re-frame/dispatch [:ws-in %])))

(defn ^:export init [] 
  (routes/app-routes)
  (set-ws-ch)
  (re-frame/dispatch-sync [:initialize-db])
  (mount-root)
  (figwheel/start {:websocket-url "ws://146.175.15.30:3449/figwheel-ws"}))
