(ns cleebo.query.components.snippet-modal
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [react-bootstrap.components :as bs]
            [taoensso.timbre :as timbre]))

(defn fetch-more-snippet [hit-idx snippet-size delta context]
  (re-frame/dispatch
   [:fetch-snippet hit-idx :snippet-size (+ snippet-size delta) :context context]))

(defn more-button [hit-idx snippet-size delta-atom snippet-delta context glyph]
  (fn [hit-idx snippet-size delta-atom snippet-delta context glyph]
    [:div.text-center
     [:i.round-button
      {:on-click (fn [] (fetch-more-snippet
                         hit-idx
                         @snippet-size
                         (swap! delta-atom + @snippet-delta)
                         context))}
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
        snippet-size (re-frame/subscribe [:settings :snippets :snippet-size])
        snippet-delta (re-frame/subscribe [:settings :snippets :snippet-delta])
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
           [:div.row
            [:div.col-sm-5]
            [:div.col-sm-1
             [more-button hit-idx snippet-size left-delta snippet-delta :left "menu-up"]]
            [:div.col-sm-1
             [more-button hit-idx snippet-size right-delta snippet-delta :right "menu-down"]]
            [:div.col-sm-5]]
           [:br] [:br]
           [text-snippet left match right]])]])))
