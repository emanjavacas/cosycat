(ns cleebo.debug.page
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [taoensso.timbre :as timbre]
            [react-bootstrap.components :as bs]
            [cleebo.utils :refer [nbsp]]
            [cleebo.schemas.app-state-schemas :refer [db-schema]]
            [cleebo.localstorage :as ls]))

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
   {:on-click
    #(re-frame/dispatch
      [:notify {:message "Hello World! How are you doing?"}])}
   "Notify"])

(defn style [depth] {:margin-left (str (+ 10 depth) "px") :cursor "pointer"})

(defn i [k v depth children & {:keys [tag] :or {tag :div}}]
  (let [open (reagent/atom true)]
    (fn [k v depth children & {:keys [tag] :or {tag :div}}]
      [tag
       {:style (merge (style depth))}
       [:span
        [:span {:on-click #(do (.stopPropagation %)  (swap! open not))}
         [:i.glyphicon
          {:class (if @open "glyphicon-triangle-bottom" "glyphicon-triangle-right")}]]
        (str k)]
       [:div {:style {:margin-left "15px"}} (when @open children)]])))

(defn recursive* [data depth]
  (cond (map? data)        (into [:div {:style (style depth)}]
                                 (mapv (fn [[k v]] [i k v depth (recursive* v (inc depth)) :tag :div])
                                       data))
        (sequential? data) (into [:ul {:style (style depth) :list-style-type "none"}]
                                 (mapv (fn [[k v]] [i k v depth (recursive* v (inc depth)) :tag :li])
                                       (map-indexed vector data)))
        :else (str data)))

(defn recursive [data] [recursive* data 0])

(defn data-tree []
  (let [db (re-frame/subscribe [:db])]
    (fn []
      [recursive @db])))

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
     [:div.row [:hr]]
     [:div.row [data-tree]]]))
