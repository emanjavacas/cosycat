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
   {:on-click #(re-frame/dispatch [:dump-db])}
   "Dump to LocalStorage"])

(defn ls-read []
  [bs/button
   {:on-click #(let [dump (ls/recover-db)]
                 (.log js/console dump))}
   "Print LocalStorage to console"])

(defn ls-reset []
  [bs/button
   {:on-click #(if-let [dump (ls/recover-db)]
                 (do
                   (timbre/info "Reloaded db from LocalStorage")
                   (re-frame/dispatch [:load-db dump]))
                 (timbre/info "Couldn't reload db from LocalStorage"))}
   "Reload db from LocalStorage"])

(defn notification-button []
  [bs/button
   {:on-click #(re-frame/dispatch [:notify {:msg "Hello World! How are you doing?"}])}
   "Notify"])

(defn open-modal [open?]
  (fn [open?]
    [bs/button
     {:on-click #(reset! open? true)}
     "Launch modal"]))

(defn debug-panel []
  (let [open? (reagent/atom false)]
    (fn []
      [:div.container-fluid
       [:div.row
        [:h3 [:span.text-muted "Debug Panel"]]]
       [:div.row [:hr]]
       [:div.row
        [bs/button-toolbar
         [notification-button]
         [ls-dump]
         [ls-read]
         [ls-reset]
         [open-modal open?]]
        [bs/modal
         {:show @open? :on-hide #(reset! open? false)}
         [bs/button
          {:on-click #(reset! open? false)}
          "Close"]]]
       [:div.row [summary-session]]])))
