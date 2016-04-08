(ns cleebo.settings.components.notification-settings
  (:require [re-frame.core :as re-frame]
            [react-bootstrap.components :as bs]
            [cleebo.components :refer [dropdown-select]]))

(defn notification-settings []
  (let [settings (re-frame/subscribe [:settings])]
    (fn []
      [:div.container-fluid
       [:div.row.align-left
        [bs/label {:style {:font-size "14px" :line-height "2.5em"}} "Notifications options"]]
       [:div.row [:hr]]
       [:div.row.text-center
        [bs/input]]])))
