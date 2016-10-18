(ns cosycat.updates.page
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [react-bootstrap.components :as bs]
            [cosycat.components :refer [css-transition-group]]
            [cosycat.updates.components.event-component :refer [event-component]]
            [taoensso.timbre :as timbre]))

(defn events-panel []
  (let [events (re-frame/subscribe [:events])]
    (fn []
      [bs/list-group
       [css-transition-group {:transition-name "updates"
                              :transition-enter-timeout 650
                              :transition-leave-timeout 650}
        (doall (for [{:keys [id type payload] :as event} (sort-by :timestamp > (vals @events))]
                 ^{:key id} [event-component event]))]])))

(defn updates-panel []
  (let [active-project (re-frame/subscribe [:active-project :name])]
    (fn []
      [:div.container-fluid
       [:div.row
        [:h3 [:span.text-muted "Updates (what's new in " [:strong @active-project] ")"]]]
       [:div.row [:hr]]
       [:div.container-fluid
        [:div.row [events-panel]]]])))
