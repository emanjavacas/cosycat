(ns cleebo.query.components.snippet-modal
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [react-bootstrap.components :as bs]
            [taoensso.timbre :as timbre]))

(defn fetch-more-snippet [hit-idx new-snippet-size context]
  (re-frame/dispatch
   [:fetch-snippet hit-idx :snippet-size new-snippet-size :context context]))

(defn snippet-modal []
  (let [snippet-modal? (re-frame/subscribe [:modals :snippet])
        snippet-size (re-frame/subscribe [:settings :snippet-size])
        left-delta (reagent/atom 0)
        right-delta (reagent/atom 0)]
    (fn []
      [bs/modal
       {:show (boolean @snippet-modal?)
        :on-hide #(do (re-frame/dispatch [:close-modal :snippet])
                      (reset! left-delta 0)
                      (reset! right-delta 0))}
       [bs/modal-header
        {:closeButton true}
        [:h4 "Snippet"]]
       [bs/modal-body
        (let [{:keys [snippet hit-idx]} @snippet-modal?
              {:keys [left match right]} snippet]
          [:div.text-justify
           [:div.text-center
            [:i.round-button
             {:on-click #(fetch-more-snippet
                          hit-idx
                          (+ @snippet-size (swap! left-delta + 15))
                          :left)}
             [bs/glyphicon {:glyph "menu-up"}]]]
           [:br]
           left
           [:p.text-center
            {:style {:font-weight "bold" :margin-bottom 0}}
            match]
           right
           [:br] [:br]
           [:div.text-center
            [:i.round-button
             {:on-click #(fetch-more-snippet
                          hit-idx
                          (+ @snippet-size (swap! right-delta + 15))
                          :right)}
             [bs/glyphicon {:glyph "menu-down"}]]]])]])))
