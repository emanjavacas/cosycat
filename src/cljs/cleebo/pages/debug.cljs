(ns cleebo.pages.debug
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [re-com.core :as re-com]))

(defn kv-pairs [s]
  (into [:div]
        (map
         (fn [[k v]]
           [re-com/h-box
            :gap "50px"
            :style {:width "40%"}
            :justify :between
            :children
            [[:div (str k)]
             [:div (str v)]]])
         s)))

(defn summary-session []
  (let [session (re-frame/subscribe [:session])]
    (fn []
      (let [asession @session ;(update-in @session [:query-results] dissoc :results)
            query-opts (:query-opts asession)
            query-results (:query-results asession)]
        (conj
         [re-com/v-box
          :gap "15px"
          :children
          [[:h4 [:span.text-muted "Query Options"]]
           [kv-pairs query-opts]
           [:h4 [:span.text-muted "Query Options"]]
           [kv-pairs query-results]]])))))

(defn debug-panel []
  [re-com/v-box
   :children
   [[:h3 [:span.text-muted "Debug Panel"]]
    [re-com/line]
    [summary-session]]])
