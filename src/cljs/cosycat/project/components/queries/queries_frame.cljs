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

(defn query-component [{{:keys [query-str corpus]} :query-data discarded :discarded timestamp :timestamp id :id}]
  [{{:keys [query-str corpus]} :query-data discarded :discarded timestamp :timestamp id :id}]
  [:div.container-fluid
   [:div.row.pull-right
    [:span {:style {:cursor "pointer"} :onClick #(re-frame/dispatch [:open-modal :remove-query id])}
     [bs/glyphicon {:glyph "trash"}]]]
   [:div.row
    [bs/table {:id "query-metadata-table"}
     [:tbody
      [:tr
       [:td [:strong "Query String"]]
       [:td [:code {:style {:margin-left "50px"}} query-str]]]    
      [:tr
       [:td [:strong "Corpus"]]
       [:td [:span.text-muted {:style {:margin-left "50px"}} corpus]]]
      [:tr
       [:td [:strong "Created"]]
       [:td [:span.text-muted {:style {:margin-left "50px"}} (human-time timestamp)]]]
      [:tr
       [:td [:strong "Discarded hits"]]
       [:td [:span.text-muted {:style {:margin-left "50px"}} (count discarded)]]]]]]])

(defn queries-frame []
  (let [project-queries (re-frame/subscribe [:project-queries :filter-corpus false])
        show? (re-frame/subscribe [:modals :remove-query])]
    (println @project-queries)
    (fn []
      [:div.container-fluid
       [remove-query-modal show?]
       [bs/list-group
        (doall (for [{id :id :as query-metadata} @project-queries]
                 ^{:key id}
                 [bs/list-group-item
                  (reagent/as-component
                   [query-component query-metadata])]))]])))
