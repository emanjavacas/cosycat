(ns cosycat.snippet
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [react-bootstrap.components :as bs]
            [taoensso.timbre :as timbre]
            [cosycat.tree :refer [data-tree]]))

(defn fetch-more-snippet [hit-id snippet-delta dir]
  (re-frame/dispatch [:fetch-snippet hit-id {:snippet-delta snippet-delta :dir dir}]))

(defn more-button [hit-id snippet-size snippet-delta dir glyph]
  (let [delta-state (atom snippet-delta)]
    (fn [hit-id snippet-size snippet-delta dir glyph]
      [:div.text-center
       [:i.round-button
        {:on-click #(let [snippet-delta (swap! delta-state + snippet-delta)]
                      (fetch-more-snippet hit-id snippet-delta dir))}
        [bs/glyphicon {:glyph glyph}]]])))

(defn metadata-button [metadata-show?]
  [:i.round-button
   {:on-click #(swap! metadata-show? not)}
   [bs/glyphicon {:glyph "file"}]])

(defn text-snippet [left match right]
  (fn [left match right]
    [:div
     (interpose " " (map :word left))
     [:p.text-center
      {:style {:font-weight "bold" :margin-bottom 0}}
      (interpose " " (map :word match))]
     (interpose " " (map :word right))]))

(defn metadata [meta]
  (fn [meta]
    [:div.row
     {:style {:padding "22px 0"
              :background-color "#f9f6f6"
              :overflow-y "scroll"
              :max-height "200px"}}
     [data-tree meta]]))

(defn snippet-modal [db-path]
  (let [show? (re-frame/subscribe [:modals db-path :snippet])
        metadata-show? (reagent/atom false)
        snippet-opts (re-frame/subscribe [:settings :query :snippet-opts])
        snippet-data (re-frame/subscribe [:snippet-data db-path])]
    (fn [db-path]
      [bs/modal
       {:show (boolean @show?)}
       [bs/modal-header
        {:closeButton true
         :onHide #(do (re-frame/dispatch [:unset-snippet-data])
                      (re-frame/dispatch [:close-modal db-path :snippet]))}
        [:h4 "Snippet"]]
       [bs/modal-body
        (let [{:keys [snippet-delta snippet-size]} @snippet-opts
              {{:keys [left match right]} :snippet {:keys [id meta]} :hit-map} @snippet-data]
          [:div.container-fluid.text-justify
           [:div.row {:style {:padding "10px 0"}}
            [:div.col-sm-4]
            [:div.col-sm-1
             [more-button id snippet-size snippet-delta :left "menu-up"]]
            [:div.col-sm-1
             [more-button id snippet-size snippet-delta :right "menu-down"]]
            [:div.col-sm-1]
            [:div.col-sm-1
             [metadata-button metadata-show?]]
            [:div.col-sm-4]]
           (when @metadata-show? [metadata meta])
           [:div.row {:style {:padding "10px 0"}} [text-snippet left match right]]])]])))
