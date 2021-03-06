(ns cosycat.query.components.minimize-panel
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [react-bootstrap.components :as bs]))

(defn default-header [] [:div ""])

(defn minimize-panel-header [open-header closed-header {:keys [id open]}]
  (fn [open-header closed-header {:keys [id open]}]
    [:div.container-fluid
     [:div.row
      [:span.pull-right
       [bs/button {:onClick #(re-frame/dispatch [:swap-panels])} [bs/glyphicon {:glyph "sort"}]]]
      (if @open [open-header] [closed-header])]]))

(defn minimize-panel
  [{:keys [child args id init open-header closed-header]
    :or {init true open-header default-header close-header default-header}}]
  (let [open (re-frame/subscribe [:project-session :components :panel-open id])]
    (fn [{:keys [child init args]}]
      [bs/panel
       {:collapsible true
        :expanded @open
        :header (reagent/as-component
                 [minimize-panel-header open-header closed-header {:id id :open open}])}
       (into [child] args)])))
