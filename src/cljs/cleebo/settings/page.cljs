(ns cleebo.settings.page
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [react-bootstrap.components :as bs]
            [cleebo.settings.components.session-settings
             :refer [session-settings]]
            [cleebo.settings.components.notification-settings
             :refer [notification-settings]]))

(defn tabs [active-tab]
  (fn [active-tab]
    [bs/nav
     {:bsStyle "tabs"
      :active-key @active-tab
      :on-select #(reset! active-tab (keyword %))}
     [bs/nav-item {:event-key :session}
      [:span {:style {:font-weight "bold"}} "Session Settings"]]
     [bs/nav-item {:event-key :notification}
      [:span {:style {:font-weight "bold"}} "Notification Settings"]]]))

(defmulti tab-panel identity)
(defmethod tab-panel :session [] [session-settings])
(defmethod tab-panel :notification [] [notification-settings])

(defn settings-panel []
  (let [active-tab (reagent/atom :session)]
    (fn []
      [:div.container-fluid
       [bs/panel {:header (reagent/as-component [tabs active-tab])}
        [:div.container-fluid [tab-panel @active-tab]]]])))
