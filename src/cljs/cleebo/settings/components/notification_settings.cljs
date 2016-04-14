(ns cleebo.settings.components.notification-settings
  (:require [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [react-bootstrap.components :as bs]
            [cleebo.components :refer [dropdown-select]]
            [cleebo.settings.components.shared-components
             :refer [row-component]]
            [cleebo.backend.db :refer [default-db]]
            [taoensso.timbre :as timbre]))

(def help-map
  {:delay "Set a time (in msec) to wait until notifications fade out"})

(defn on-mouse-over [target text-atom]
  (fn [e] (reset! text-atom (get help-map target))))

(defn on-mouse-out [text-atom]
  (fn [e] (reset! text-atom "")))

(defn on-click [f]
  (fn []
    (re-frame/dispatch
     [:update-notification
      [:delay]
      f])))

(defn get-default [path]
  (fn []
    (get-in default-db path)))

(defn notification-controller []
  (let [notification-help (reagent/atom "")
        delay (re-frame/subscribe [:settings :notifications :delay])]
    (fn []
      [row-component
       :label "Notification Delay"
       :controllers [:div.btn-toolbar
                     [:div.input-group
                      {:style {:width "150px"}
                       :on-mouse-over (on-mouse-over :delay notification-help)
                       :on-mouse-out (on-mouse-out notification-help)}
                      [:span.input-group-btn
                       [:button.btn.btn-default
                        {:type "button"
                         :on-click (on-click (fn [d] (max 0 (- d 250))))}
                        [bs/glyphicon {:glyph "minus"}]]]
                      [:span.form-control.input-number @delay]
                      [:span.input-group-btn
                       [:button.btn.btn-default
                        {:type "button"
                         :on-click (on-click (fn [d] (+ d 250)))}
                        [bs/glyphicon {:glyph "plus"}]]]]
                     [:button.btn.btn-default
                      {:type "button"
                       :on-click (on-click (get-default [:settings :notifications :delay]))}
                      "Set default"]]
       :help-text notification-help])))

(defn notification-settings []
  [:div.container-fluid
   [notification-controller]])
