(ns cleebo.settings.page
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [react-bootstrap.components :as bs]
            [cleebo.settings.components.session-settings
             :refer [session-settings]]
            [cleebo.settings.components.notification-settings
             :refer [notification-settings]]
            [cleebo.settings.components.appearance-settings
             :refer [appearance-settings]]))

(defn tabs [active-tab expanded?]
  (fn [active-tab expanded?]
    [bs/nav
     {:bsStyle "tabs"
      :active-key @active-tab
      :on-select #(reset! active-tab (keyword %))}
     [bs/nav-item {:event-key :session}
      [:span {:style {:font-weight "bold"}} "Session Settings"]]
     [bs/nav-item {:event-key :notification}
      [:span {:style {:font-weight "bold"}} "Notification Settings"]]
     [bs/nav-item {:event-key :appearance}
      [:span {:style {:font-weight "bold"}} "Appearance Settings"]]
     [:span.pull-right
      {:style {:cursor "pointer"}
       :on-click #(swap! expanded? not)}
      [bs/glyphicon {:glyph (if @expanded? "resize-small" "resize-full")}]]]))

(defmulti tab-panel identity)
(defmethod tab-panel :session [] [session-settings])
(defmethod tab-panel :notification [] [notification-settings])
(defmethod tab-panel :appearance [] [appearance-settings])

(defn settings-panel []
  (let [active-tab (reagent/atom :session)
        expanded? (reagent/atom false)]
    (fn []
      [:div
       {:class (if @expanded? "container-fluid" "container")}
       [bs/panel {:header (reagent/as-component [tabs active-tab expanded?])}
        [:div.container-fluid [tab-panel @active-tab]]
        [bs/button
         {:onClick #(js/alert "To be implemented")
          :class "pull-right"
          :bsStyle "info"}
         "Save settings"]]])))
