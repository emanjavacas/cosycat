(ns cosycat.project.components.events.events-frame
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [react-bootstrap.components :as bs]
            [cosycat.utils :refer [->map]]
            [cosycat.components :refer [css-transition-group dropdown-select]]
            [cosycat.project.components.events.event-component :refer [event-component]]
            [taoensso.timbre :as timbre]))

(defn read-more-button [from]
  (fn [from]
    [bs/button
     {:style {:width "40px" :height "40px" :line-height "40px" :padding "0" :border-radius "50%"}
      :onClick #(re-frame/dispatch [:fetch-user-project-events :from from :max-events 5])}
     [bs/glyphicon {:glyph "menu-down"}]]))

(defn fetch-project-events [project]
  (re-frame/dispatch [:fetch-user-project-events])
  (re-frame/dispatch [:fetch-project-events project]))

(defn event-filter [events]
  (let [type-sub (re-frame/subscribe [:project-session :components :event-filters :type])]
    (fn [events]
      [bs/button-toolbar
       [dropdown-select
        {:label "type: "
         :header "Filter events by type"
         :model @type-sub
         :options (mapv #(->map % %) (->> @events (mapv :type) (into #{"all"})))
         :select-fn
         #(re-frame/dispatch [:set-project-session-component [:event-filters :type] %])}]])))

(defn should-display-event?
  [{event-type :type} {type-filter :type}]
  (or (= "all" type-filter) (= event-type type-filter)))

(defn events-panel [events event-filters]
  (fn [events event-filters]
    [:div
     (if (empty? @events)
       [:div.text-center [:h2.text-muted "This project doesn't have events"]]
       [bs/list-group
        [css-transition-group {:transition-name "updates"
                               :transition-enter-timeout 650
                               :transition-leave-timeout 650}
         (doall (for [{:keys [id type payload] :as event} (sort-by :timestamp > @events)
                      :when (should-display-event? event @event-filters)]
                  ^{:key id} [event-component event]))]])]))

(defn events-frame []
  (let [active-project (re-frame/subscribe [:active-project :name])
        event-filters (re-frame/subscribe [:project-session :components :event-filters])
        events (re-frame/subscribe [:events])]
    (reagent/create-class
     {:component-will-mount #(fetch-project-events @active-project)
      :component-will-update #(fetch-project-events @active-project)
      :reagent-render
      (fn []
        [:div.container-fluid
         [:div.row.pull-right [:div.col-lg-12 [event-filter events]]]
         [:div.row {:style {:height "50px"}}]
         [:div.row  [:div.col-lg-12 [events-panel events event-filters]]]
         [:div.row.text-center [read-more-button (->> @events (sort-by :timestamp >) last :timestamp)]]
         [:div.row {:style {:height "40px"}}]])})))
