(ns cosycat.core
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [cosycat.backend.handlers.db]
            [cosycat.backend.handlers.users]
            [cosycat.backend.handlers.components]
            [cosycat.backend.handlers.settings]
            [cosycat.backend.handlers.query]
            [cosycat.backend.handlers.annotations]
            [cosycat.backend.handlers.notifications]
            [cosycat.backend.handlers.session]
            [cosycat.backend.handlers.projects]
            [cosycat.backend.handlers.corpora]
            [cosycat.backend.handlers.events]
            [cosycat.backend.ws-routes]
            [cosycat.backend.subs]
            [cosycat.query.page :refer [query-panel]]
            [cosycat.project.page :refer [project-panel]]
            [cosycat.settings.page :refer [settings-panel]]
            [cosycat.admin.page :refer [admin-panel]]
            [cosycat.front.page :refer [front-panel]]
            [cosycat.error.page :refer [error-panel]]
            [cosycat.review.page :refer [review-panel]]
            [cosycat.backend.ws :refer [open-ws-channel]]
            [cosycat.key-bindings :refer [bind-panel-keys]]
            [cosycat.routes :as routes]
            [cosycat.localstorage :as ls]
            [cosycat.navbar :refer [navbar]]
            [cosycat.components
             :refer [notification-container session-message-modal throbbing-panel]]
            [cosycat.ajax-interceptors
             :refer [add-interceptor csrf-interceptor ajax-header-interceptor debug-interceptor]]
            [taoensso.timbre :as timbre]
            [clojure.string :as str]))

(defn loading-panel []
  [:div.container-fluid
   [:div.row.text-center
    {:style {:height "250px"}}
    [throbbing-panel :css-class :loader-ticks]]
   [:div.row.text-center
    [:h2.text-muted "Loading Database"]]])

(defmulti panels identity)
(defmethod panels :front-panel [] [#'front-panel])
(defmethod panels :query-panel [] [#'query-panel])
(defmethod panels :review-panel [] [#'review-panel])
(defmethod panels :project-panel [] [#'project-panel])
(defmethod panels :settings-panel [] [#'settings-panel])
(defmethod panels :admin-panel [] [#'admin-panel])
(defmethod panels :error-panel [] [#'error-panel])
(defmethod panels :loading-panel [] [#'loading-panel])

(defn has-session-error? [session-error] session-error)

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
  (re-frame/dispatch [:initialize-session])
  ;; declare app routes
  (routes/app-routes)
  ;; handle refreshes
  (routes/nav! "/")
  ;; (.addEventListener js/window "beforeunload" ls/dump-db) ;; disable this (too buggy)
  ;; render root
  (mount-root))
