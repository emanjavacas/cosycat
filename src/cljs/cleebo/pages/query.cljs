(ns cleebo.pages.query
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [re-com.core :as re-com]
            [chord.client :refer [ws-ch]]
            [cljs.core.async :refer [<! >! put! close!]]
            [taoensso.timbre :as timbre])
  (:require-macros [cljs.core.async.macros :refer [go]]))

;;; ws
(go
  (let [{:keys [ws-channel error]} (<! (ws-ch "ws://146.175.15.30:3000/ws" {:format :transit-json}))]
    (if-not error
      (>! ws-channel "Hi there!")
      (timbre/debug (pr-str error)))))

(defn query-field []
  [:h1.page-header {:style {:font-weight "5em"}}
   [:div.row
    [:div.col-sm-3 "Query Panel"]
    [:div.col-sm-9
     [:div.form-horizontal
      [:div.row
       [:div.col-sm-10
        [:div.form-group
         [:input.form-control
          {:name "query"
           :type "text"
           :id "query"
           :placeholder "Example: [pos='.*\\.']"
           :autocorrect "off"
           :autocapitalize "off"
           :spellcheck "false"}]]]
       [:div.col-sm-2.col-sm-
        [:div.form-group
         [:button.btn.btn-primary.btn-md "Query!"]]]]]]]])

(defn query-results []
  [:div])

(defn query-panel []
  [:div
   [query-field]
   [query-results]])
