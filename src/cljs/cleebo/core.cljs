(ns cleebo.core
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [react-bootstrap.components :as bs]
            [cleebo.backend.handlers.db]
            [cleebo.backend.handlers.components]
            [cleebo.backend.handlers.settings]
            [cleebo.backend.handlers.query]
            [cleebo.backend.handlers.snippet]
            [cleebo.backend.handlers.annotations]
            [cleebo.backend.handlers.notifications]
            [cleebo.backend.handlers.session]
            [cleebo.backend.ws :refer [make-ws-channels!]]
            [cleebo.backend.ws-routes]
            [cleebo.backend.subs]
            [cleebo.routes :as routes]
            [cleebo.localstorage :as ls]
            [cleebo.components :refer [notification-container load-from-ls-modal user-thumb]]
            [cleebo.query.page :refer [query-panel]]
            [cleebo.annotation.page :refer [annotation-panel]]
            [cleebo.settings.page :refer [settings-panel]]
            [cleebo.updates.page :refer [updates-panel]]
            [cleebo.debug.page :refer [debug-panel]]
            [cleebo.front.page :refer [front-panel]]
            [cleebo.utils :refer [nbsp]]
            [taoensso.timbre :as timbre]
            [devtools.core :as devtools]
            [clojure.string :as str]))

(defmulti panels identity)
(defmethod panels :front-panel [] [front-panel])
(defmethod panels :query-panel [] [query-panel])
(defmethod panels :settings-panel [] [settings-panel])
(defmethod panels :debug-panel [] [debug-panel])
(defmethod panels :updates-panel [] [updates-panel])
(defmethod panels :annotation-panel [] [annotation-panel])

(defn icon-label [icon label]
  [:span [:i {:class (str "zmdi " icon)      
              :style {:line-height "20px"
                      :font-size "15px"
                      :margin-right "5px"}}]
   label])

(defn navlink [target href label icon]
  (let [active (re-frame/subscribe [:active-panel])]
    (fn []
      [bs/nav-item
       {:eventKey target
        :class (if (= @active target) "active")
        :href href}
       [icon-label icon label]])))

(defn navdropdown [target href label icon & children]
  (let [active (re-frame/subscribe [:active-panel])]
    (fn []
      [bs/nav-dropdown
       {:eventKey target
        :id "dropdown"
        :class (if (= @active target) "active")
        :href href
        :title (reagent/as-component [icon-label icon label])}
       (for [[idx {:keys [label href on-select]}] (map-indexed vector children)
             :let [k (str label idx)]]
         ^{:key k} [bs/menu-item
                    {:eventKey k
                     :href href
                     :onSelect on-select}
                    label])])))

(defn user-brand []
  (let [username (re-frame/subscribe [:session :user :username])]
    (fn []
      [bs/navbar-brand
       [:div.container-fluid
        {:style {:margin-top "-9.5px"}}
        [:div.row
         {:style {:line-height "40px" :text-align "right"}}
         [:div.col-sm-8
          (str (nbsp 10) (str/capitalize @username))]
         [:div.col-sm-4 [user-thumb @username {:height "30px" :width "30px"}]]]]])))

(defn navbar [active-panel]
  (fn [active-panel]
    [bs/navbar
     {:inverse false
      :fixedTop true
      :fluid true}
     [bs/navbar-header
      [user-brand]]
     [bs/nav {:pullRight true}
      (when-not (= @active-panel :front-panel)
        [navlink :query-panel "#/query" "Query" "zmdi-search"])
      (when-not (= @active-panel :front-panel)
        [navlink :annotation-panel "#/annotation" "Annotation" "zmdi-edit"])
      [navlink :updates-panel "#/updates" "Updates" "zmdi-notifications"]
      [navlink :settings-panel "#/settings" "Settings" "zmdi-settings"]
      [navdropdown :debug-panel "#/debug" "Debug" "zmdi-bug" ;debug mode
       {:label "Debug page" :href "#/debug"}
       {:label "App snapshots" :on-select #(re-frame/dispatch [:open-modal :localstorage])}
       {:label "New backup" :on-select ls/dump-db}]
      [navlink :exit "#/exit" "Exit" "zmdi-power"]]]))

(defn main-panel []
  (let [active-panel (re-frame/subscribe [:active-panel])
        ls-modal? (re-frame/subscribe [:modals :localstorage])]
    (fn []
      [:div
       [navbar active-panel]
       [notification-container]
       [load-from-ls-modal ls-modal?]
       [:div.container-fluid
        {:style {:padding "75px 50px 0 50px"}}
        (panels @active-panel)]])))

(defn mount-root []
  (reagent/render [#'main-panel] (.getElementById js/document "app")))

(defn ^:export init []
  ;; init devtools
  (devtools/enable-feature! :sanity-hints :dirac)
  
  (devtools/install!)
  ;; declare app routes
  (routes/app-routes)
  ;; web-sockets
  (make-ws-channels!)
  ;; start db
  (re-frame/dispatch-sync [:initialize-db])
  ;; fetch user data
  (re-frame/dispatch [:fetch-user-session])
  ;; handle refreshes
  (.addEventListener js/window "beforeunload" ls/dump-db)
  ;; render root
  (mount-root))
