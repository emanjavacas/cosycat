(ns cleebo.query.components.snippet-modal
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [react-bootstrap.components :as bs]
            [taoensso.timbre :as timbre]))

(defn fetch-more-snippet [hit-idx snippet-size delta context]
  (re-frame/dispatch
   [:fetch-snippet hit-idx :snippet-size (+ snippet-size delta) :context context]))

(defn more-button [hit-idx snippet-size delta context glyph]
  (fn [hit-idx snippet-size delta-atom context glyph]
    [:div.text-center
     [:i.round-button
      {:on-click #(fetch-more-snippet hit-idx @snippet-size (swap! delta + 10) context)}
      [bs/glyphicon {:glyph glyph}]]]))

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
    (re-frame/dispatch [:close-modal :snippet])
    (reset! left-delta 0)
    (reset! right-delta 0)))

(defn snippet-modal []
  (let [snippet-modal? (re-frame/subscribe [:modals :snippet])
        snippet-size (re-frame/subscribe [:settings :snippet-size])
        left-delta (reagent/atom 0)
        right-delta (reagent/atom 0)]
    (fn []
      [bs/modal
       {:show (boolean @snippet-modal?)
        :on-hide (on-hide left-delta right-delta)}
       [bs/modal-header
        {:closeButton true}
        [:h4 "Snippet"]]
       [bs/modal-body
        (let [{:keys [snippet hit-idx]} @snippet-modal?
              {:keys [left match right]} snippet]
          [:div.text-justify
           [more-button hit-idx snippet-size left-delta :left "menu-up"]
           [:br] [:br]
           [text-snippet left match right]
           [:br] [:br]
           [more-button hit-idx snippet-size right-delta :right "menu-down"]])]])))
