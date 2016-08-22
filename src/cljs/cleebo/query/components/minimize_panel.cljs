(ns cleebo.query.components.minimize-panel
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [react-bootstrap.components :as bs]))

(defn default-header [] [:div ""])

(defn minimize-panel-header [open-header closed-header {:keys [id dir open]}]
  (fn [open-header closed-header {:keys [id dir open]}]
    [:div.container-fluid
     [:div.row
      [:span.pull-right
       [bs/button-toolbar
        [bs/button
         {:onClick #(re-frame/dispatch [:panel-order id dir]) :bsSize "xsmall"}
         [bs/glyphicon {:glyph (if (= :bottom dir) "chevron-down" "chevron-up")}]]
        [bs/button
         {:onClick #(re-frame/dispatch [:panel-open id (not @open)]) :bsSize "xsmall"}
         [bs/glyphicon {:glyph (if @open "collapse-up" "collapse-down")}]]]]
      (if @open [open-header] [closed-header])]]))

(defn minimize-panel
  [{:keys [child args id init open-header closed-header]
    :or {init true open-header default-header close-header default-header}}]
  (let [open (re-frame/subscribe [:project-session :components :panel-open id])
        panel-order (re-frame/subscribe [:project-session :components :panel-order])]
    (fn [{:keys [child init args]}]
      (let [dir (if (= id (first @panel-order)) :bottom :top)]
        [bs/panel
         {:collapsible true
          :expanded @open
          :header (reagent/as-component
                   [minimize-panel-header open-header closed-header {:id id :dir dir :open open}])}
         (into [child] args)]))))
