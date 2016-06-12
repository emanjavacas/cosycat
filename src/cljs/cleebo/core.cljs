(ns cleebo.core
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [react-bootstrap.components :as bs]
            [cleebo.backend.handlers.db]
            [cleebo.backend.handlers.components]
            [cleebo.backend.handlers.settings]
            [cleebo.backend.handlers.query]
            [cleebo.backend.handlers.annotations]
            [cleebo.backend.handlers.notifications]
            [cleebo.backend.handlers.session]
            [cleebo.backend.handlers.projects]
            [cleebo.backend.history]            
            [cleebo.backend.ws-routes]
            [cleebo.backend.subs]
            [cleebo.backend.ws :refer [open-ws-channel]]
            [cleebo.routes :as routes]
            [cleebo.localstorage :as ls]
            [cleebo.components :refer [notification-container load-from-ls-modal user-thumb]]
            [cleebo.query.page :refer [query-panel]]
            [cleebo.settings.page :refer [settings-panel]]
            [cleebo.updates.page :refer [updates-panel]]
            [cleebo.debug.page :refer [debug-panel]]
            [cleebo.front.page :refer [front-panel]]
            [cleebo.error.page :refer [error-panel]]            
            [cleebo.utils :refer [nbsp]]
            [cleebo.ajax-interceptors
             :refer [add-interceptor csrf-interceptor ajax-header-interceptor]]
            [taoensso.timbre :as timbre]
            [clojure.string :as str]))

(defmulti panels identity)
(defmethod panels :front-panel [] [#'front-panel])
(defmethod panels :query-panel [] [#'query-panel])
(defmethod panels :settings-panel [] [#'settings-panel])
(defmethod panels :debug-panel [] [#'debug-panel])
(defmethod panels :updates-panel [] [#'updates-panel])
(defmethod panels :error-panel [] [#'error-panel])

(defn icon-label [icon label]
  [:span [:i {:class (str "zmdi " icon)      
              :style {:line-height "20px"
                      :font-size "15px"
                      :margin-right "5px"}}]
   label])

(defn user-brand-span [username active-project]
  (let [projects (re-frame/subscribe [:session :user-info :projects])]
    (fn [username active-project]
      [:div (str/capitalize username)
       [:span {:style {:white-space "nowrap"}}
        (if-let [{project-name :name} @active-project]
          (str "@" project-name))]])))

(defn user-brand [active-project]
  (let [user (re-frame/subscribe [:session :user-info])]
    (fn [active-project]
      (let [{username :username {href :href} :avatar} @user]
        [bs/navbar-brand
         [:div.container-fluid
          {:style {:margin-top "-9.5px"}}
          [:div.row
           {:style {:line-height "40px" :text-align "right"}}
           [:div.col-sm-8
            ;; wait until user-info is fetched in main
            (when username [user-brand-span username active-project])]
           [:div.col-sm-4
            ;; wait until user-info is fetched in main
            (when username [user-thumb href {:height "30px" :width "30px"}])]]]]))))

(defn navlink [target href label icon]
  (let [active (re-frame/subscribe [:active-panel])]
    (fn []
      [bs/nav-item
       {:eventKey target
        :class (if (= @active target) "active")
        :href href}
       [icon-label icon label]])))

(defn navdropdown [target label icon & {:keys [children]}]
  (let [active (re-frame/subscribe [:active-panel])]
    (fn [target label icon & {:keys [children]}]
      [bs/nav-dropdown
       {:eventKey target
        :id "dropdown"
        :class (if (= @active target) "active")
        :title (reagent/as-component [icon-label icon label])}
       (for [[idx {:keys [label href on-select style] :as args}] (map-indexed vector children)
             :let [k (str label idx)]]
         ^{:key k} [bs/menu-item
                    (merge {:eventKey k
                            :style style
                            :href href
                            :onSelect on-select}
                           args)
                    label])])))

(defn projects-dropdown [projects active-project]
  (fn [projects active-project]
    [navdropdown :no-panel "Projects" "zmdi-toys"
     :children
     (concat
      [{:label "Projects page" :href "#/"}
       {:divider true}
       {:label "Projects" :header true}]
      (doall
       (for [{project-name :name} @projects]
         {:label project-name
          :href (str "#/project/" project-name)
          :style (when (= project-name (:name @active-project))
                   {:background-color "#e7e7e7"
                    :color "black"})})))]))

(defn debug-dropdown []
  [navdropdown :debug-panel "Debug" "zmdi-bug" ;debug mode
   :children
   [{:label "Debug page" :href "#/debug"}
    {:label "App snapshots"
     :on-select #(re-frame/dispatch [:open-modal :localstorage])}
    {:label "New backup"
     :on-select ls/dump-db}]])

(defn navbar [active-panel]
  (let [active-project (re-frame/subscribe [:active-project])
        projects (re-frame/subscribe [:session :user-info :projects])]
    (fn [active-panel]
      [bs/navbar
       {:inverse false
        :responsive true
        :fixedTop true
        :fluid true}
       [bs/navbar-header [user-brand active-project]]
       [bs/nav {:pullRight true}
        (when-not (= @active-panel :front-panel)
          [navlink :query-panel (str "#/project/" (:name @active-project))
           "Query" "zmdi-search"])
        (when-not (= @active-panel :front-panel)
          [navlink :updates-panel "#/updates" "Updates" "zmdi-notifications"])
        (when-not (= @active-panel :front-panel)
          [navlink :settings-panel "#/settings" "Settings" "zmdi-settings"])
        (when-not (or (= @active-panel :front-panel) (empty? @projects))
          [projects-dropdown projects active-project])
;        [debug-dropdown]
        [navlink :exit "#/exit" "Exit" "zmdi-power"]]])))

(defn main-panel []
  (let [active-panel (re-frame/subscribe [:active-panel])
        ls-modal? (re-frame/subscribe [:modals :localstorage])]
    (fn []
      (timbre/debug @active-panel)
      [:div
       [navbar active-panel]
       [notification-container]
       [load-from-ls-modal ls-modal?]
       [:div.container-fluid
        {:style {:padding "75px 50px 0 50px"}}
        (panels @active-panel)]])))

(defn mount-root []
  (reagent/render [main-panel] (.getElementById js/document "app")))

(defn host-url []
  (str "ws://" (.-host js/location) "/ws"))

(defn init! []
  ;; install csrf-token
  (add-interceptor csrf-interceptor {:csrf-token js/csrf})
  (add-interceptor ajax-header-interceptor)
  ;; web-sockets
  (open-ws-channel {:url (host-url)})
  ;; start db
  (re-frame/dispatch-sync
   [:initialize-db
    {:session {:throbbing? {:front-panel true}}}])
  ;; fetch user data and projects
  (re-frame/dispatch [:init-session])
  ;; ensure we start on home page (so that db can be loaded)
  (routes/nav! "/")
  ;; declare app routes
  (routes/app-routes)
  ;; handle refreshes
  (.addEventListener js/window "beforeunload" ls/dump-db)
  ;; render root
  (mount-root))
