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
       [bs/button-toolbar
        (let [other-panel (if (= id "query-frame") "annotation-panel" "query-frame")
              dir (if @open :bottom :top)]
          [bs/button {:onClick #(do (re-frame/dispatch [:panel-order id dir])
                                    (re-frame/dispatch [:panel-open id (not @open)])
                                    (re-frame/dispatch [:panel-open other-panel @open]))}
           [bs/glyphicon {:glyph "sort"}]])]]
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
