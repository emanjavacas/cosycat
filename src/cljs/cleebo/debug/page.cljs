(ns cleebo.debug.page
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
  (let [query-opts (re-frame/subscribe [:query-opts])
        query-results (re-frame/subscribe [:query-results])
        results (re-frame/subscribe [:session :results])
        marked-hits (re-frame/subscribe [:marked-hits])]
    (fn []
      (conj
       [:div.container-fluid
        [:div.row [:h4 [:span.text-muted "Query Options"]]]
        [:div.row [kv-pairs @query-opts]]
        [:div.row [:h4 [:span.text-muted "Query Results"]]]
        [:div.row [kv-pairs @query-results]]
        [:div.row [:h4 [:span.text-muted "Results"]]]
        [:div.row [kv-pairs @results]]
        [:div.row [:h4 [:span.text-muted "Marked hits"]]]
        [:div.row [kv-pairs @marked-hits]]
        [:div.row [:h4 [:span.text-muted "Selected tokens"]]]
;        [:div.row [kv-pairs ]]
        [:div.row [:h4 [:span.text-muted "Match Ids"]]]
        [:div.row
         (map :id (filter :match (mapcat :hit (vals @results))))]]))))

(defn debug-panel []
  [:div.container-fluid
   [:div.row
    [:h3 [:span.text-muted "Debug Panel"]]]
   [:div.row [:hr]]
   [:div.row [summary-session]]])
