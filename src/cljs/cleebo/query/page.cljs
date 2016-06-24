(ns cleebo.query.page
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [react-bootstrap.components :as bs]
            [cleebo.utils :refer [format]]
            [cleebo.query.components.highlight-error :refer [highlight-error]]
            [cleebo.query.components.query-toolbar :refer [query-toolbar]]
            [cleebo.query.components.results-table :refer [results-table]]
            [cleebo.query.components.results-toolbar :refer [results-toolbar]]
            [cleebo.query.components.sort-toolbar :refer [sort-toolbar]]
            [cleebo.query.components.snippet-modal :refer [snippet-modal]]
            [cleebo.annotation.components.annotation-panel :refer [annotation-panel]]
            [cleebo.components :refer
             [error-panel throbbing-panel minimize-panel filter-annotation-buttons]]
            [taoensso.timbre :as timbre]))

(defn internal-error-panel [content]
  (fn [content]
    [error-panel
     :status "Oops! something bad happened"
     :content [:div content]]))

(defn query-error-panel [content]
  (fn [content]
    [error-panel
     :status (str "Query misquoted starting at position " (inc (:at content)))
     :content (highlight-error content)]))

(defn no-results-panel [query-str]
  (fn [query-str]
    [error-panel :status (format "No matches found for query: %s" @query-str)]))

(defn do-research-panel []
  [error-panel :status "No hits to be shown... Go do some research!"])

(defn has-error [status]
  (= status :error))

(defn has-query-error [status]
  (= status :query-str-error))

(defn no-results [query-str query-size]
  (and (not (= "" query-str)) (zero? query-size)))

(defn has-results [query-size]
  (not (zero? query-size)))

(defn has-marked-hits [marked-hits]
  (not (zero? (count marked-hits))))

(defn results-frame []
  (let [status (re-frame/subscribe [:session :query-results :status])
        query-size (re-frame/subscribe [:session :query-results :query-size])
        query-str (re-frame/subscribe [:session :query-results :query-str])
        throbbing? (re-frame/subscribe [:throbbing? :results-frame])]
    (fn []
      (let [{:keys [status content]} @status]
        (cond
          @throbbing?                         [throbbing-panel]
          (has-error status)                  [internal-error-panel content]
          (has-query-error status)            [query-error-panel content]
          (no-results @query-str @query-size) [no-results-panel query-str]
          (has-results @query-size)           [results-table])))))

(defn query-frame []
  (fn []
    [:div.container-fluid
     [query-toolbar]
     [:div.row {:style {:margin-top "5px"}}]
     [sort-toolbar]
     [:hr]
     [results-toolbar]
     [:div.row {:style {:margin-top "5px"}}]
     [results-frame]]))

(defn unmark-all-hits-btn []
  [bs/button
   {:onClick #(re-frame/dispatch [:unmark-all-hits])
    :style {:font-size "12px" :height "34px"}}
   "Unmark hits"])

(defn label-closed-header [label]
  (fn []
    [:div.container-fluid [:div.row [:div.col-lg-10 [:div label]]]]))

(defn query-panel-closed-header []
  (let [query-str (re-frame/subscribe [:session :query-results :query-str])
        query-size (re-frame/subscribe [:session :query-results :query-size])]
    (fn []
      [:div.container-fluid
       [:div.row
        [:div.col-lg-10 (str "Query (" @query-str "); Total Results (" @query-size ")")]]])))

(defn annotation-closed-header []
  (let [marked-hits (re-frame/subscribe [:marked-hits {:has-marked? false}])]
    (fn []
      [:div.container-fluid
       [:div.row
        [:div.col-lg-10
         (str "Annotation panel (" (count @marked-hits) " selected hits)")]]])))

(defn annotation-open-header []
  (fn []
    [:div.container-fluid
     [:div.row
      [:div.col-lg-2.pull-left [filter-annotation-buttons]]
      [:div.col-lg-2]
      [:div.col-lg-3.pull-right [unmark-all-hits-btn]]]]))

(defn query-panel []
  (let [query-size (re-frame/subscribe [:session :query-results :query-size])
        marked-hits (re-frame/subscribe [:marked-hits {:has-marked? false}])
        throbbing? (re-frame/subscribe [:throbbing? :results-frame])]
    (fn []
      [:div.container-fluid.pad
       {:style {:width "100%" :padding "0px 10px 0px 10px"}}
       [:div.row [minimize-panel
                  {:child query-frame
                   :open-header (label-closed-header "Query Panel")
                   :closed-header query-panel-closed-header}]]
       (when (has-marked-hits @marked-hits)
         [:div.row [minimize-panel
                    {:child annotation-panel
                     :closed-header annotation-closed-header
                     :open-header annotation-open-header
                     :init true}]])
       [snippet-modal]])))
