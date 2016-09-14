(ns cosycat.query.components.snippet-modal
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

(defn metadata-button [metadata-show]
  [:i.round-button
   {:on-click #(swap! metadata-show not)}
   [bs/glyphicon {:glyph "file"}]])

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
        snippet-opts (re-frame/subscribe [:settings :query :snippet-opts])
        hit-map (re-frame/subscribe [:snippet-hit])
        metadata-show (reagent/atom false)]
    (fn []
      [bs/modal
       {:show (boolean @snippet-modal?)}
       [bs/modal-header
        {:closeButton true
         :onHide #(re-frame/dispatch [:close-modal :snippet])}
        [:h4 "Snippet"]]
       [bs/modal-body
        (let [{{:keys [left match right]} :snippet hit-id :hit-id} @snippet-modal?
              {:keys [snippet-delta snippet-size]} @snippet-opts
              {:keys [hit meta]} @hit-map]
          [:div.container-fluid.text-justify
           [:div.row {:style {:padding "10px 0"}}
            [:div.col-sm-4]
            [:div.col-sm-1
             [more-button hit-id snippet-size snippet-delta :left "menu-up"]]
            [:div.col-sm-1
             [more-button hit-id snippet-size snippet-delta :right "menu-down"]]
            [:div.col-sm-1]
            [:div.col-sm-1
             [metadata-button metadata-show]]
            [:div.col-sm-4]]
           (when @metadata-show
             [:div.row {:style {:padding "22px 0" :background-color "#f9f6f6"}}
              [data-tree meta]])
           [:div.row {:style {:padding "10px 0"}} [text-snippet left match right]]])]])))
