(ns cleebo.components
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [cleebo.utils :refer [css-transition-group]]
            [react-bootstrap.components :as bs]))

(defn error-panel [& {:keys [status status-content]}]
  {:pre [(and status)]}
  [:div.container-fluid.text-center
   {:style {:padding "40px"}}
   [:div.row [:h3 [:span.text-muted status]]]
   [:div.row [:br]]
   [:div.row.text-center status-content]])

(defn dropdown-select [{:keys [label model options select-fn header]}]
  (let [local-label (reagent/atom model)]
    (fn [{:keys [label model options select-fn header] :or {select-fn identity}}]
      [bs/dropdown
       {:id "Dropdown"
        :onSelect (fn [e k] (reset! local-label k) (select-fn k))}
       [bs/button
        {:style {:pointer-events "none !important"}}
        [:span.text-muted label] @local-label]
       [bs/dropdown-toggle]
       [bs/dropdown-menu
        (concat
         [^{:key "header"} [bs/menu-item {:header true} header]
          ^{:key "divider"} [bs/menu-item {:divider true}]]
         (for [{:keys [key label]} options]
           ^{:key key} [bs/menu-item {:eventKey label} label]))]])))

(defn notification [id message]
  ^{:key id}
  [:li#notification
   {:on-click #(re-frame/dispatch [:drop-notification id])}
   message])

(defn notification-container [notifications]
  [:ul#notifications
   {:style {:position "fixed"
            :right "5px"
            :top "55px"
            :z-index "1001"}}
   [css-transition-group
    {:transition-name "notification"
     :transition-enter-timeout 5000
     :transition-leave-timeout 5000}
    (map (fn [[id {msg :msg date :date}]]
           (notification id (str msg " " id " " (.toDateString date))))
         @notifications)]])
