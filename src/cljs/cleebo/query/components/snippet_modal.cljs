(ns cleebo.query.components.snippet-modal
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [react-bootstrap.components :as bs]
            [taoensso.timbre :as timbre]))

(defn fetch-more-snippet [hit-idx snippet-size snippet-delta context]
  (re-frame/dispatch
   [:fetch-snippet hit-idx :snippet-size (+ snippet-size snippet-delta) :context context]))

(defn more-button [hit-idx snippet-size snippet-delta context glyph]
  (let [delta-state (reagent/atom 0)]
    (fn [hit-idx snippet-size snippet-delta context glyph]
      [:div.text-center
       [:i.round-button
        {:on-click #(let [snippet-delta (swap! delta-state + @snippet-delta)]
                      (fetch-more-snippet hit-idx @snippet-size snippet-delta context))}
        [bs/glyphicon {:glyph glyph}]]])))

(defn text-snippet [left match right]
  (fn [left match right]
    [:div
     left
     [:p.text-center
      {:style {:font-weight "bold" :margin-bottom 0}}
      match]
     right]))

(defn on-hide [left-delta right-delta]
  (fn []
    (re-frame/dispatch [:close-modal :snippet])))

(defn snippet-modal []
  (let [snippet-modal? (re-frame/subscribe [:modals :snippet])
        snippet-size (re-frame/subscribe [:settings :snippets :snippet-size])
        snippet-delta (re-frame/subscribe [:settings :snippets :snippet-delta])]
    (fn []
      [bs/modal
       {:show (boolean @snippet-modal?)}
       [bs/modal-header
        {:closeButton true}
        [:h4 "Snippet"]]
       [bs/modal-body
        (let [{:keys [snippet hit-idx]} @snippet-modal?
              {:keys [left match right]} snippet]
          [:div.text-justify
           [:div.row
            [:div.col-sm-5]
            [:div.col-sm-1
             [more-button hit-idx snippet-size snippet-delta :left "menu-up"]]
            [:div.col-sm-1
             [more-button hit-idx snippet-size snippet-delta :right "menu-down"]]
            [:div.col-sm-5]]
           [:br] [:br]
           [text-snippet left match right]])]])))
