(ns cleebo.core
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [react-bootstrap.components :as bs]
            [cleebo.backend.handlers]
            [cleebo.backend.ws-routes.router]
            [cleebo.backend.ws-routes.annotation-route]
            [cleebo.backend.subs]
            [cleebo.routes :as routes]
            [cleebo.ws :as ws]
            [cleebo.localstorage :as ls]
            [cleebo.components :refer
             [notification-container load-from-ls-modal]]
            [cleebo.query.page :refer [query-panel]]
            [cleebo.annotation.page :refer [annotation-panel]]
            [cleebo.settings.page :refer [settings-panel]]
            [cleebo.updates.page :refer [updates-panel]]
            [cleebo.debug.page :refer [debug-panel]]
            [cleebo.utils :refer [nbsp]]
            [taoensso.timbre :as timbre]
            [figwheel.client :as figwheel]
            [devtools.core :as devtools]
            [clojure.string :as str])
  (:require-macros [cleebo.env :as env :refer [cljs-env]]))

(defmulti panels identity)
(defmethod panels :query-panel [] [query-panel])
(defmethod panels :settings-panel [] [settings-panel])
(defmethod panels :debug-panel [] [debug-panel])
(defmethod panels :updates-panel [] [updates-panel])
(defmethod panels :annotation-panel [] [annotation-panel])

(defn navlink [target href label icon]
  (let [active (re-frame/subscribe [:active-panel])]
    (fn []
      [bs/nav-item
       {:eventKey target
        :class (if (= @active target) "active")
        :href href}
       [:span [:i {:class (str "zmdi " icon)      
                   :style {:line-height "20px"
                           :font-size "15px"
                           :margin-right "5px"}}]
        label]])))

(defn navdropdown [target href label icon & children]
  (let [active (re-frame/subscribe [:active-panel])]
    (fn []
      [bs/nav-dropdown
       {:eventKey target
        :id "dropdown"
        :class (if (= @active target) "active")
        :href href
        :title label}
       (for [[idx {:keys [label href on-select]}] (map-indexed vector children)
             :let [k (str label idx)]]
         ^{:key k} [bs/menu-item
                    {:eventKey k
                     :href href
                     :onSelect on-select}
                    label])])))

(defn navbar []
  [bs/navbar
   {:inverse true
    :fixedTop true
    :fluid true}
   [bs/navbar-header
    [bs/navbar-brand (str (nbsp :n 6) "Hello " (str/capitalize js/username) "!")]]
   [bs/nav {:pullRight true}
    [navlink :query-panel "#/query" "Query" "zmdi-search"]
    [navlink :annotation-panel "#/annotation" "Annotation" "zmdi-edit"]
    [navlink :updates-panel "#/updates" "Updates" "zmdi-notifications"]
    [navlink :settings-panel "#/settings" "Settings" "zmdi-settings"]
    [navdropdown :debug-panel "#/debug" "Debug" "zmdi-bug"
     {:label "Debug page" :href "#/debug"}
     {:label "App snapshots" :on-select #(re-frame/dispatch [:open-ls-modal])}
     {:label "New backup" :on-select ls/dump-db}]
    [navlink :exit          "#/exit" "Exit" "zmdi-power"]]])

(defn main-panel []
  (let [active-panel (re-frame/subscribe [:active-panel])
        open-modal? (re-frame/subscribe [:open-ls-modal])]
    (fn []
      [:div
       [navbar]
       [notification-container]
       [load-from-ls-modal open-modal?]
       [:div.container-fluid
        {:style {:padding "75px 50px 0 50px"}}
        (panels @active-panel)]])))

(defn mount-root []
  (reagent/render [#'main-panel] (.getElementById js/document "app")))

(defn ^:export init []
  (let [host (cljs-env :host)]
    ;; init devtools
    (devtools/enable-feature! :sanity-hints :dirac)
    (devtools/install!)
    ;; declare app routes
    (routes/app-routes)
    ;; web-sockets
    (ws/set-ws-ch)
    ;; start db
    (re-frame/dispatch-sync [:initialize-db])
    ;; handle refreshes
    (.addEventListener js/window "beforeunload" ls/dump-db)
    ;; render root
    (mount-root)
    ;; start figwheel server
    (figwheel/start {:websocket-url (str "ws://" host ":3449/figwheel-ws")})))
