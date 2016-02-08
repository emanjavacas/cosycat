(ns cleebo.pages.debug
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]))

(defn kv-pairs [s]
  (into [:div]
        (map
         (fn [[k v]]
           [:div.row
            {:style {:width "95%"}}
            [:div.col-sm-2 (str k)]
            [:div.col-sm-10 (str v)]])
         s)))

(defn summary-session []
  (let [session (re-frame/subscribe [:session])]
    (fn []
      (let [asession @session ;(update-in @session [:query-results] dissoc :results)
            query-opts (:query-opts asession)
            query-results (:query-results asession)]
        (conj
         [:div.container-fluid
          [:div.row [:h4 [:span.text-muted "Query Options"]]]
          [:div.row [kv-pairs query-opts]]
          [:div.row [:h4 [:span.text-muted "Query Results"]]]
          [:div.row [kv-pairs query-results]]
          [:div.row [:h4 [:span.text-muted "Match Ids"]]]
          [:div.row
           (map :id (filter :match (mapcat :hit (vals (:results query-results)))))]])))))

(defn debug-panel []
  [:div.container-fluid
   [:div.row
    [:h3 [:span.text-muted "Debug Panel"]]]
   [:div.row [:hr]]
   [:div.row [summary-session]]])
