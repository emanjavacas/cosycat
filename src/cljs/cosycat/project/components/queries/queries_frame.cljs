(ns cosycat.project.components.queries.queries-frame
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [react-bootstrap.components :as bs]
            [cosycat.components :refer [css-transition-group]]
            [cosycat.utils :refer [human-time]]
            [taoensso.timbre :as timbre]))

(defn double-check-button [modal-data]
  (fn [modal-data]
    [:div.text-center
     [bs/button-group
      [bs/button
       {:bsStyle "primary"
        :onClick #(do (re-frame/dispatch [:close-modal :remove-query])
                      (re-frame/dispatch [:query-drop-metadata @modal-data]))}
       "Yes"]
      [bs/button
       {:onClick #(re-frame/dispatch [:close-modal :remove-query])}
       "No"]]]))

(defn remove-query-modal [show?]
  (fn [show?]
    [bs/modal
     {:show (boolean @show?)
      :onHide #(re-frame/dispatch [:close-modal :remove-query])}
     [bs/modal-header
      [:h3 "Are you sure you want to remove this query?"]]
     [bs/modal-body
      [:div
       [double-check-button show?]]]]))

(defn counter-row [text hits]
  [:div.row.highlightable
   [:div.col-lg-4.col-md-4.col-sm-4 [:span "Hits marked as " [:strong text]]]
   [:div.col-lg-8.col-md-8.col-sm-8 [:span.text-muted (count hits)]]])

(defn filter-opts-row [filter-opts]
  [:div.row.highlightable
   [:div.col-lg-4.col-md-4.col-sm-4 [:span [:strong "Query filters"]]]
   [:div.col-lg-8.col-md-8.col-sm-8 [:span.text-muted]]])

(defn sort-opts-row [sort-opts]
  [:div.row.highlightable
   [:div.col-lg-4.col-md-4.col-sm-4 [:span [:strong "Query sort criteria"]]]
   [:div.col-lg-8.col-md-8.col-sm-8 [:span.text-muted]]])

(defn query-component
  [{{:keys [query-str corpus filter-opts sort-opts]} :query-data {:keys [kept discarded]} :hits
    timestamp :timestamp id :id creator :creator default :default}]
  (fn [{{:keys [query-str corpus filter-opts sort-opts]} :query-data {:keys [kept discarded]} :hits
        timestamp :timestamp id :id creator :creator default :default}]
    [:div.container-fluid
     [:div.row
      [:div.col-lg-10.col-sm-10
       [:span [:h4 id]
        [:span.text-muted "Created by " [:strong creator] " on " (human-time timestamp)]]]
      [:div.col-lg-2.col-sm-2.text-right
       [bs/button
        {:style {:cursor "pointer"} :onClick #(re-frame/dispatch [:open-modal :remove-query id])}
        [bs/glyphicon {:glyph "trash"}]]]]
     [:div.row {:style {:height "10px"}}]
     [:div.row.highlightable
      [:div.col-lg-4.col-md-4.col-sm-4 [:strong "Query String"]]
      [:div.col-lg-8.col-md-8.col-sm-8 [:code query-str]]]
     (when filter-opts [filter-opts-row filter-opts])
     (when sort-opts [sort-opts-row sort-opts])
     [:div.row.highlightable
      [:div.col-lg-4.col-md-4.col-sm-4 [:strong "Corpus"]]
      [:div.col-lg-8.col-md-8.col-sm-8 [:span.text-muted corpus]]]
     [:div.row.highlightable
      [:div.col-lg-4.col-md-4.col-sm-4 [:strong "Default hit value"]]
      [:div.col-lg-8.col-md-8.col-sm-8 [:span.text-muted default]]]
     (when (not= default "unseen")
       (if (= default "discarded")
         [counter-row "kept" kept]
         [counter-row "discarded" discarded]))
     (when (= default "unseen")
       [counter-row "kept" kept])
     (when (= default "unseen")
       [counter-row "discarded" discarded])]))

(defn queries-frame []
  (let [project-queries (re-frame/subscribe [:project-queries :filter-corpus false])
        show? (re-frame/subscribe [:modals :remove-query])]
    (fn []
      [:div.container-fluid
       [remove-query-modal show?]
       [bs/list-group
        (doall (for [{id :id :as query-metadata} @project-queries]
                 ^{:key id}
                 [bs/list-group-item
                  (reagent/as-component
                   [query-component query-metadata])]))]])))
