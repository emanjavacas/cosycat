(ns cleebo.settings.page
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [react-bootstrap.components :as bs]))

(def tooltip (reagent/as-component [bs/tooltip "Hi"]))
(def example-anns [{:key "animacy" :value "M"} {:key "aspect" :value "perfect"}])
(def popover (reagent/as-component
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

(defn settings-panel []
  [:div.container-fluid
   [:div.row [:h3 [:span.text-muted "Settings"]]]
   [:div.row [:hr]]
   [:div.row
    [bs/overlay-trigger
     {:overlay popover}
     [bs/button [:span [bs/glyphicon {:glyph "pencil"}] " Hi!"] ]]]])
