(ns cosycat.admin.components.db
  (:require [re-frame.core :as re-frame]
            [react-bootstrap.components :as bs]
            [cosycat.localstorage :refer [dump-db fetch-last-dump get-backups]]
            [taoensso.timbre :as timbre]))

(defn ls-dump []
  [bs/button {:on-click dump-db} "Dump to LocalStorage"])

(defn ls-print []
  [bs/button {:on-click #(timbre/debug (get-backups))} "Print LocalStorages to console"])

(defn ls-reload []
  [bs/button
   {:on-click #(if-let [dump (fetch-last-dump)]
                 (re-frame/dispatch [:load-db dump])
                 (timbre/info "No DBs in LocalStorage"))}
   "Reload last db from LocalStorage"])

(defn notification-button []
  [bs/button
   {:on-click
    #(re-frame/dispatch
      [:notify {:message "Hello World! How are you doing?"}])}
   "Notify"])

(defn db-frame []
  [:div.container-fluid
   [:div.row
    [:div.col-lg-12 [bs/button-toolbar
                     [ls-dump]
                     [ls-print]
                     [ls-reload]
                     [notification-button]]]]])
