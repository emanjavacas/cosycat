(ns cleebo.core
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [cleebo.backend.handlers]
            [cleebo.backend.subs]
            [cleebo.routes :as routes]
            [cleebo.logic.ws :refer [make-ws-ch]]
            [cleebo.pages.query :refer [query-panel]]
            [cleebo.pages.settings :refer [settings-panel]]
            [cleebo.pages.debug :refer [debug-panel]]
            [cleebo.utils :refer [notify! ;css-transition-group
                                  ]]
            [taoensso.timbre :as timbre]
            [figwheel.client :as figwheel])
  (:require-macros [cleebo.env :as env :refer [cljs-env]]))

(defmulti panels (fn [panel-key & args] panel-key))
(defmethod panels :query-panel [panel-key & {:keys [visible?]}]
  (timbre/debug @visible?)
  [query-panel visible?])
(defmethod panels :settings-panel [] [settings-panel])
(defmethod panels :debug-panel [] [debug-panel])
(defmethod panels :default [] [:div])

(defn sidelink [target href label icon]
  (let [active (re-frame/subscribe [:active-panel])]
    (fn []
      [:li {:class (when (= @active target) "active")}
       [:a {:href href :style {:color "#fff2ef"}}
        [:span [:i {:class (str "zmdi " icon)                    
                    :style {:line-height "20px"
                            :font-size "15px"
                            :margin-right "5px"}}]
         label]]])))

(defn notification [id message]
  ^{:key id}
  [:li#notification
   {:on-click #(re-frame/dispatch [:drop-notification id])}
   message])

;; (defn notification-container [notifications]
;;   [css-transition-group {:transition-name "notification"}
;;    (map (fn [[id {msg :msg date :date}]]
;;           (notification id (str msg " " id " " (.toDateString date))))
;;         @notifications)])

(defn annotation-panel [visible?]
  [:div.menu
   [:div.right
    {:class (if @visible? "visible")}]])

(defn main-panel []
  (let [active-panel (re-frame/subscribe [:active-panel])
        notifications (re-frame/subscribe [:notifications])
        visible? (reagent/atom false)]
    (fn []
      [:div
       [annotation-panel visible?]
       [:div.container-fluid
        [:ul#notifications
         {:style {:position "fixed"
                  :right "5px"
                  :top "5px"
                  :z-index "1001"}}
                                        ;        [notification-container notifications]
         ]
        [:div.row 
         [:div.col-sm-2.col-md-1.sidebar ;sidebar
          [:ul.nav.nav-sidebar
           [sidelink :home-panel "#/home" "Home" "zmdi-home"]
           [sidelink :query-panel "#/query" "Query" "zmdi-search"]
           [sidelink :updates-panel "#/updates" "Updates" "zmdi-notifications"]
           [sidelink :settings-panel "#/settings" "Settings" "zmdi-settings"]
           [sidelink :debug-panel "#/debug" "Debug" "zmdi-bug"]          
           [sidelink :exit          "#/exit" "Exit" "zmdi-power"]]]
         [:div.col-sm-10.col-sm-offset-2.col-md-11.col-md-offset-1.main
          (panels @active-panel :visible? visible?)]]]])))

(defn mount-root []
  (.log js/console "Called mount-root")
  (reagent/render [#'main-panel] (.getElementById js/document "app")))

(defn set-ws-ch []
  (make-ws-ch
   (str "ws://" (.-host js/location) "/ws")
   #(re-frame/dispatch [:ws-in %])))

(defn ^:export init []
  (let [host (cljs-env :host)]
    (routes/app-routes)
    (set-ws-ch)
    (re-frame/dispatch-sync [:initialize-db])
    (mount-root)
    (figwheel/start {:websocket-url (str "ws://" host ":3449/figwheel-ws")})))
