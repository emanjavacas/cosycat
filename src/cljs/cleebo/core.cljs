(ns cleebo.core
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [react-bootstrap.components :as bs]
            [cleebo.backend.handlers]
            [cleebo.backend.subs]
            [cleebo.routes :as routes]
            [cleebo.ws :as ws]
            [cleebo.localstorage :as ls]
            [cleebo.components :refer [notification-container notification]]
            [cleebo.query.page :refer [query-panel]]
            [cleebo.annotation.page :refer [annotation-panel]]
            [cleebo.settings.page :refer [settings-panel]]
            [cleebo.updates.page :refer [updates-panel]]
            [cleebo.debug.page :refer [debug-panel]]
            [cleebo.utils :refer [coerce-json nbsp]]
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
    [navlink :debug-panel "#/debug" "Debug" "zmdi-bug"]          
    [navlink :exit          "#/exit" "Exit" "zmdi-power"]]])

(defn load-from-ls-modal [open?]
  (fn [open?]
    [bs/modal
     {:show @open? :on-hide #(reset! open? false)}
     [bs/modal-header
      [bs/modal-title
       [:div "Watch out!" [:span.pull-right [:i.zmdi.zmdi-storage]]]]]
     [bs/modal-body
      [:p "Cleebo found unsaved activities in your browser."]
      [:p "Do you want to restore it? Select 'yes' or 'no'"]
      [:br]
      [:p.text-muted "Note that you might not be able to restore it later"]]
     [bs/modal-footer
      [bs/button-toolbar
       {:className "pull-right"}
       [bs/button
        {:on-click #(let [dump (ls/fetch :db :coercion-fn (coerce-json))]
                      (timbre/debug (:active-panel dump))
                      (re-frame/dispatch [:load-db dump])
                      (re-frame/dispatch [:close-init-modal]))}
        "yes"]
       [bs/button
        {:on-click #(re-frame/dispatch [:close-init-modal])}
        "no"]]]]))

(defn main-panel []
  (let [active-panel (re-frame/subscribe [:active-panel])
        open-modal? (re-frame/subscribe [:open-init-modal])
        notifications (re-frame/subscribe [:notifications])]
    (fn []
      [:div
       [navbar]
       [notification-container notifications]
       [load-from-ls-modal open-modal?]
       [:div.container-fluid
        {:style {:padding "75px 50px 0 50px"}}
        (panels @active-panel)]])))

(defn mount-root []
  (reagent/render [#'main-panel] (.getElementById js/document "app")))

(defn ^:export init []
  (let [host (cljs-env :host)
        is-open? (reagent/atom false)]
    ;; init devtools
    (devtools/enable-feature! :sanity-hints :dirac)
    (devtools/install!)
    ;; declare app routes
    (routes/app-routes)
    ;; web-sockets
    (ws/set-ws-ch)
    ;; start db
    (re-frame/dispatch-sync [:initialize-db])
    (if-let [dump (ls/fetch :db :coercion-fn (coerce-json))]
      (re-frame/dispatch-sync [:open-init-modal]))
    ;; handle refreshes
    (.addEventListener js/window "beforeunload" #(re-frame/dispatch [:dump-db]))
    ;; render root
    (mount-root)
    ;; start figwheel server
    (figwheel/start {:websocket-url (str "ws://" host ":3449/figwheel-ws")})))
