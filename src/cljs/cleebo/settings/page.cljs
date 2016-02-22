(ns cleebo.settings.page
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [react-bootstrap.components :as bs]))

(def example-anns [{:key "animacy" :value "M"} {:key "aspect" :value "perfect"}])
(def popover
  (reagent/as-component
   [bs/popover
    {:title "Hi there, I am a panel and you?"}
    [:table {:width "100%"}
     [:thead]
     [:tbody
      (for [{:keys [key value]} example-anns]
        ^{:key (str value)}
        [:tr {:style {:padding "15px !important"}}
         [:td {:style {:padding-bottom "10px" :text-align "left"}} key]
         [:td {:style {:text-align "right"}}
          (if value [bs/label value])]])]]]))

(def overlay-component
  [bs/overlay-trigger
   {:overlay popover :placement "left"}
   [bs/button [:span [bs/glyphicon {:glyph "pencil"}] " Hi!"] ]])

(defn tabs [active-tab]
  (fn [active-tab]
    [bs/nav
     {:bsStyle "tabs"
      :active-key @active-tab
      :on-select #(reset! active-tab (keyword %))}
     [bs/nav-item {:event-key :global} "Global Settings"]
     [bs/nav-item {:event-key :notification} "Notification Settings"]]))

(defn global-settings []
  [:div "Global"])

(defn notification-settings []
  [:div "Notifications"])

(defmulti tab-panel identity)
(defmethod tab-panel :global [] [global-settings])
(defmethod tab-panel :notification [] [notification-settings])

(defn settings-panel []
  (let [active-tab (reagent/atom :global)]
    (fn []
      [:div.container-fluid
       [:div.row
        [:div.col-lg-4 [:h3 [:span.text-muted "Settings"]]]
        [:div.col-lg-8.text-right overlay-component]]
       [:br]
       [bs/panel {:header (reagent/as-component [tabs active-tab])}
        [tab-panel @active-tab]]])))

