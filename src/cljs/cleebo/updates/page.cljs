(ns cleebo.updates.page
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [taoensso.timbre :as timbre]))

(defn history-panel []
  (let [history (re-frame/subscribe [:read-history [:ws]])]
    (fn []
      [:div.container-fluid
       (doall (for [[idx item] (map-indexed vector (sort-by :timestamp > @history))]
                ^{:key idx}
                [:div.row (str item)]))])))

(defn updates-panel []
  [:div.container-fluid
   [:div.row [:h3 [:span.text-muted "Updates (what's new in your feed)"]]]
   [:div.row [:hr]]
   [history-panel]])
