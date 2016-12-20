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

(defn filter-opts-row [filter-opts]
  [:div.row.highlightable
   [:div.col-lg-6.col-md-6.col-sm-6 [:span [:strong "Filters"]]]
   [:div.col-lg-6.col-md-6.col-sm-6 [:span.text-muted]]])

(defn sort-opts-row [sort-opts]
  [:div.row.highlightable
   [:div.col-lg-6.col-md-6.col-sm-6 [:span [:strong "Sort criteria"]]]
   [:div.col-lg-6.col-md-6.col-sm-6 [:span.text-muted]]])

(defn query-component
  [{{:keys [query-str corpus filter-opts sort-opts]} :query-data hits :hits
    description :description timestamp :timestamp id :id creator :creator default :default}]
  (let [me (re-frame/subscribe [:me :username])]
    (fn [{{:keys [query-str corpus filter-opts sort-opts]} :query-data hits :hits
          description :description timestamp :timestamp id :id creator :creator default :default}]
      [:div.container-fluid
       [:div.row.pad
        [:div.container-fluid
         [:div.row
          [:div.col-lg-10.col-sm-10
           [:p {:style {:font-size "18px" :margin-bottom "2px"}} id]
           [:span description]
           [:br]
           [:span.text-muted "Created by " [:strong creator] " on " (human-time timestamp)]]
          [:div.col-lg-2.col-sm-2.text-right
           (when (= creator @me)        ;only creator can delete query
               [bs/button
                {:style {:cursor "pointer"}
                 :onClick #(re-frame/dispatch [:open-modal [:remove-query] id])}
                [bs/glyphicon {:glyph "trash"}]])]]]]
       [:div.row.pad
        [:div.col-lg-6.col-md-6.col-sm-6
         [:div.container-fluid.pad
          [:div.row {:style {:height "10px"}}]
          [:div.row.highlightable
           [:div.col-lg-6.col-md-6.col-sm-6 [:strong "Query String"]]
           [:div.col-lg-6.col-md-6.col-sm-6 [:code query-str]]]
          [:div.row.highlightable
           [:div.col-lg-6.col-md-6.col-sm-6 [:strong "Corpus"]]
           [:div.col-lg-6.col-md-6.col-sm-6 [:span.text-muted corpus]]]
          [:div.row.highlightable
           [:div.col-lg-6.col-md-6.col-sm-6 [:strong "Default hit value"]]
           [:div.col-lg-6.col-md-6.col-sm-6 [:span.text-muted default]]]]]
        [:div.col-lg-6.col-md-6.col-sm-6
         [:div.container-fluid.pad
          (when filter-opts [filter-opts-row filter-opts])
          (when sort-opts   [sort-opts-row sort-opts])]]]])))

(defn queries-frame []
  (let [project-queries (re-frame/subscribe [:project-queries :filter-corpus false])
        show? (re-frame/subscribe [:modals :remove-query])]
    (fn []
      [:div.container-fluid
       [remove-query-modal show?]
       (if (empty? @project-queries)
         [:div.text-center [:h2.text-muted "This project doesn't have queries yet"]]
         [bs/list-group
          (doall (for [{id :id :as query-metadata} @project-queries]
                   ^{:key id}
                   [bs/list-group-item
                    (reagent/as-component
                     [query-component query-metadata])]))])])))
