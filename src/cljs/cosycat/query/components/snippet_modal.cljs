(ns cosycat.query.components.snippet-modal
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [react-bootstrap.components :as bs]
            [taoensso.timbre :as timbre]))

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

(defn text-snippet [left match right]
  (fn [left match right]
    [:div
     left
     [:p.text-center
      {:style {:font-weight "bold" :margin-bottom 0}}
      match]
     right]))

(defn snippet-modal []
  (let [snippet-modal? (re-frame/subscribe [:modals :snippet])
        snippet-opts (re-frame/subscribe [:settings :query :snippet-opts])]
    (fn []
      [bs/modal
       {:show (boolean @snippet-modal?)}
       [bs/modal-header
        {:closeButton true
         :onHide #(re-frame/dispatch [:close-modal :snippet])}
        [:h4 "Snippet!"]]
       [bs/modal-body
        (let [{:keys [snippet hit-id]} @snippet-modal?
              {:keys [left match right]} snippet
              {:keys [snippet-delta snippet-size]} @snippet-opts]
          [:div.text-justify
           [:div.row
            [:div.col-sm-5]
            [:div.col-sm-1
             [more-button hit-id snippet-size snippet-delta :left "menu-up"]]
            [:div.col-sm-1
             [more-button hit-id snippet-size snippet-delta :right "menu-down"]]
            [:div.col-sm-5]]
           [:br] [:br]
           [text-snippet left match right]])]])))
