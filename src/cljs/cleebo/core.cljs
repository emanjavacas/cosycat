(ns cleebo.core
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [react-bootstrap.components :as bs]
            [cleebo.backend.handlers.db]
            [cleebo.backend.handlers.users]
            [cleebo.backend.handlers.components]
            [cleebo.backend.handlers.settings]
            [cleebo.backend.handlers.query]
            [cleebo.backend.handlers.annotations]
            [cleebo.backend.handlers.notifications]
            [cleebo.backend.handlers.session]
            [cleebo.backend.handlers.projects]
            [cleebo.backend.handlers.corpora]
            [cleebo.backend.history]            
            [cleebo.backend.ws-routes]
            [cleebo.backend.subs]
            [cleebo.query.page :refer [query-panel]]
            [cleebo.project.page :refer [project-panel]]
            [cleebo.settings.page :refer [settings-panel]]
            [cleebo.updates.page :refer [updates-panel]]
            [cleebo.debug.page :refer [debug-panel]]
            [cleebo.front.page :refer [front-panel]]
            [cleebo.error.page :refer [error-panel]]
            [cleebo.backend.ws :refer [open-ws-channel]]
            [cleebo.key-bindings :refer [bind-panel-keys]]
            [cleebo.routes :as routes]
            [cleebo.localstorage :as ls]
            [cleebo.components
             :refer [notification-container session-message-modal
                     user-thumb throbbing-panel]]
            [cleebo.app-utils :refer [function?]]
            [cleebo.utils :refer [nbsp format]]
            [cleebo.ajax-interceptors
             :refer [add-interceptor csrf-interceptor ajax-header-interceptor debug-interceptor]]
            [taoensso.timbre :as timbre]
            [clojure.string :as str]))

(defn loading-panel []
  [:div.container-fluid
   [:div.row.text-center
    {:style {:height "250px"}}
    [throbbing-panel :css-class "loader-ticks"]]
   [:div.row.text-center
    [:h2.text-muted "Loading Database"]]])

(defmulti panels identity)
(defmethod panels :front-panel [] [#'front-panel])
(defmethod panels :query-panel [] [#'query-panel])
(defmethod panels :project-panel [] [#'project-panel])
(defmethod panels :settings-panel [] [#'settings-panel])
(defmethod panels :debug-panel [] [#'debug-panel])
(defmethod panels :updates-panel [] [#'updates-panel])
(defmethod panels :error-panel [] [#'error-panel])
(defmethod panels :loading-panel [] [#'loading-panel])

(defn icon-label [icon label]
  [:span [:i {:class (str "zmdi " icon)      
              :style {:line-height "20px"
                      :font-size "15px"
                      :margin-right "5px"}}]
   label])

(defn user-brand-span [username active-project]
  (fn [username active-project]
    [:div username
     (when @active-project
       [:span {:style {:white-space "nowrap"}} (str "@" @active-project)])]))

(defn user-brand-component [active-project user]
  (fn [active-project user]
    (let [{username :username {href :href} :avatar} @user]
      (when username                    ;wait until loaded
        [:div.row
         {:style {:line-height "35px" :text-align "right"}}
         [:div.col-sm-8 
          [user-brand-span username active-project]]
         [:div.col-sm-4
          [user-thumb href {:height "30px" :width "30px"}]]]))))

(defn user-brand [active-project]
  (let [user (re-frame/subscribe [:me])
        users (re-frame/subscribe [:active-project :users])]
    (fn [active-project]
      (let [{username :username {href :href} :avatar} @user
            my-role (->> @users (filter #(= username (:username %))) first :role)
            tooltip (format "Your role is [%s] in this project" my-role)]
        [bs/navbar-brand
         [:div.container-fluid
          {:style {:margin-top "-9.5px"}}
          (if @active-project
            [bs/overlay-trigger
             {:overlay (reagent/as-component [bs/tooltip {:id "tooltip"} tooltip])}
             [:div [user-brand-component active-project user]]]
            [:div [user-brand-component active-project user]])]]))))

(defn navlink [target href label icon]
  (let [active (re-frame/subscribe [:active-panel])]
    (fn []
      [bs/nav-item
       {:eventKey target
        :class (if (= @active target) "active")
        :href (if (function? href) (href) href)}
       [icon-label icon label]])))

(defn navdropdown [target label icon & {:keys [children]}]
  (let [active (re-frame/subscribe [:active-panel])]
    (fn [target label icon & {:keys [children]}]
      [bs/nav-dropdown
       {:eventKey target
        :id "dropdown"
        :class (if (= @active target) "active")
        :title (reagent/as-component [icon-label icon label])}
       (for [[idx {:keys [label href on-select style] :as args}]
             (map-indexed vector children)
             :let [k (str label idx)]]
         ^{:key k}
         [bs/menu-item
          (merge {:eventKey k
                  :style style
                  :href (if (function? href) (href) href)
                  :onSelect on-select}
                 (dissoc args :style :href :onSelect))
          label])])))

(defn merge-target-project-url
  "computes url for project, adding subpath /query if origin was in query itself
   `cleebo#/project/project1/query` -> `cleebo#/project/project2/query`
   instead of `cleebo#/project/project1/query` -> `cleebo#/project/project2`"
  [project-name]
  (let [prefix (str "#/project/" project-name)
        origin (.-lastToken_ cleebo.routes/history)]
    (if (and origin (.endsWith origin "query"))
      (str prefix "/query")
      prefix)))

(defn projects-dropdown [projects active-project]
  (fn [projects active-project]
    [navdropdown :no-panel "Projects" "zmdi-toys"
     :children
     (concat
      [{:label "Projects page" :href "#/"}
       {:divider true}
       {:label "Projects" :header true}]
      (doall
       (for [[project-name {:keys [project]}] @projects]
         {:label project-name
          :href #(merge-target-project-url project-name)
          :style (when (= project-name @active-project)
                   {:background-color "#e7e7e7"
                    :color "black"})})))]))

(defn navbar [active-panel]
  (let [active-project (re-frame/subscribe [:session :active-project])
        projects (re-frame/subscribe [:projects])]
    (fn [active-panel]
      [bs/navbar
       {:inverse false
        :responsive true
        :fixedTop true
        :fluid true}
       [bs/navbar-header [user-brand active-project]]
       [bs/nav {:pullRight true}
        (when-not (= @active-panel :front-panel)
          (let [url #(str "#/project/" @active-project "/query")]
            [navlink :query-panel url "Query" "zmdi-search"]))
        (when-not (= @active-panel :front-panel)
          [navlink :updates-panel "#/updates" "Updates" "zmdi-notifications"])
        (when-not (= @active-panel :front-panel)
          [navlink :settings-panel "#/settings" "Settings" "zmdi-settings"])
        (when-not (or (= @active-panel :front-panel) (empty? @projects))
          [projects-dropdown projects active-project])
        [navlink :exit "#/exit" "Exit" "zmdi-power"]]])))

(defn has-session-error? [session-error]
  session-error)

(defn has-init-session? [session-init]
  (not session-init))

(defn main-panel []
  (let [active-panel (re-frame/subscribe [:active-panel])
        session-error (re-frame/subscribe [:session-has-error?])
        session-init (re-frame/subscribe [:session :init])
        session-message-modal? (re-frame/subscribe [:modals :session-message])]
    (fn []
      [:div
       [navbar active-panel]
       [notification-container]
       [session-message-modal session-message-modal?]
       [:div.container-fluid
        {:style {:padding "75px 50px 0 50px"}}
        (let [panel-key (cond
                          (has-session-error? @session-error) :error-panel
                          (has-init-session? @session-init) :loading-panel
                          :else @active-panel)]
          (bind-panel-keys panel-key)
          (panels panel-key))]])))

(defn mount-root []
  (reagent/render [main-panel] (.getElementById js/document "app")))

(defn init! []
  ;; install csrf-token & other ajax interceptors
  (add-interceptor csrf-interceptor {:csrf-token js/csrf})
  (add-interceptor ajax-header-interceptor)
  (add-interceptor debug-interceptor)
  ;; web-sockets
  (open-ws-channel {:url (str "ws://" (.-host js/location) "/ws")})
  ;; start session
  (re-frame/dispatch-sync [:initialize-session])
  ;; declare app routes
  (routes/app-routes)
  ;; handle refreshes
  ;; (.addEventListener js/window "beforeunload" ls/dump-db) ;; disable this (too buggy)
  ;; render root
  (mount-root))
