(ns cosycat.project.components.events-frame 
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [react-bootstrap.components :as bs]
            [cosycat.components :refer [css-transition-group]]
            [cosycat.project.components.event-component :refer [event-component]]
            [taoensso.timbre :as timbre]))

(defn events-panel [events]
  (fn [events]
    [bs/list-group
     [css-transition-group {:transition-name "updates"
                            :transition-enter-timeout 650
                            :transition-leave-timeout 650}
      (doall (for [{:keys [id type payload] :as event} (sort-by :timestamp > @events)]
               ^{:key id} [event-component event]))]]))

(defn read-more-button [from]
  (fn [from]
    [bs/button
     {:style {:width "40px" :height "40px" :line-height "40px" :padding "0" :border-radius "50%"}
      :onClick #(re-frame/dispatch [:fetch-user-project-events :from from :max-events 5])}
     [bs/glyphicon {:glyph "menu-down"}]]))

(defn fetch-project-events [project]
  (re-frame/dispatch [:fetch-user-project-events])
  (re-frame/dispatch [:fetch-project-events project]))

(defn events-frame []
  (let [active-project (re-frame/subscribe [:active-project :name])
        events (re-frame/subscribe [:events])]
    (reagent/create-class
     {:component-will-mount #(fetch-project-events @active-project)
      :component-will-update #(fetch-project-events @active-project)
      :reagent-render
      (fn []
        [:div.container-fluid
         [:div.row  [:div.col-lg-12 [events-panel events]]]
         [:div.row.text-center [read-more-button (->> @events (sort-by :timestamp >) last :timestamp)]]
         [:div.row {:style {:height "40px"}}]])})))
