(ns cleebo.debug.page
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [taoensso.timbre :as timbre]
            [react-bootstrap.components :as bs]
            [cleebo.backend.middleware :refer [db-schema]]
            [cleebo.localstorage :as ls]))

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
        results (re-frame/subscribe [:session :results-by-id])
        result-keys (re-frame/subscribe [:session :results])
        marked-hits (re-frame/subscribe [:marked-hits])]
    (fn []
      [:div.container-fluid
       [:div.row [:h4 [:span.text-muted "Query Options"]]]
       [:div.row [kv-pairs @query-opts]]
       [:div.row [:h4 [:span.text-muted "Query Results"]]]
       [:div.row [kv-pairs @query-results]]
       [:div.row [:h4 [:span.text-muted "Results"]]]
       (into [:div] (map (fn [k] [:div.row k]) @result-keys))
       [:div.row [:h4 [:span.text-muted "Results by key"]]]
       [:div.row [kv-pairs @results]]
       [:div.row [:h4 [:span.text-muted "Marked hits"]]]
       [:div.row [kv-pairs (map (juxt :id identity) @marked-hits)]]])))

(defn ls-dump []
  [bs/button
   {:on-click ls/dump-db}
   "Dump to LocalStorage"])

(defn ls-print []
  [bs/button
   {:on-click #(let [ks (ls/recover-all-db-keys)]
                 (.log js/console ks))}
   "Print LocalStorages to console"])

(defn ls-reload []
  [bs/button
   {:on-click #(if-let [ks (ls/recover-all-db-keys)]
                 (let [dump (ls/recover-db (last ks))]
                   (re-frame/dispatch [:load-db dump]))
                 (timbre/info "No DBs in LocalStorage"))}
   "Reload last db from LocalStorage"])

(defn notification-button []
  [bs/button
   {:on-click #(re-frame/dispatch [:notify {:msg "Hello World! How are you doing?"}])}
   "Notify"])

(defn debug-panel []
  (fn []
    [:div.container-fluid
     [:div.row
      [:h3 [:span.text-muted "Debug Panel"]]]
     [:div.row [:hr]]
     [:div.row
      [bs/button-toolbar
       [notification-button]
       [ls-dump]
       [ls-print]
       [ls-reload]]]
     [:div.row [summary-session]]]))
