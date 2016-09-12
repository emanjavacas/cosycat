(ns cosycat.query.page
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [react-bootstrap.components :as bs]
            [cosycat.utils :refer [format]]
            [cosycat.app-utils :refer [->int]]
            [cosycat.query.components.highlight-error :refer [highlight-error]]
            [cosycat.query.components.query-toolbar :refer [query-toolbar]]
            [cosycat.query.components.results-table :refer [results-table]]
            [cosycat.query.components.results-toolbar :refer [results-toolbar]]
            [cosycat.query.components.sort-toolbar :refer [sort-toolbar]]
            [cosycat.query.components.snippet-modal :refer [snippet-modal]]
            [cosycat.query.components.minimize-panel :refer [minimize-panel]]
            [cosycat.annotation.components.annotation-panel :refer [annotation-panel]]
            [cosycat.components :refer
             [error-panel throbbing-panel filter-annotation-buttons]]
            [taoensso.timbre :as timbre]))

(defn parse-query-error-msg [message]
  (let [re #"Query: (.+) ; has error at position: (\d+)"
        [_ query-str at] (first (re-seq re message))]
    [query-str (->int at)]))

(defn error-panel-by-type [content]
  (fn [{:keys [message code]}]
    (if (= code "Query string error")
      (let [[query-str at] (parse-query-error-msg message)]
        [error-panel
         :status (str "Query misquoted starting at position " at)
         :content (highlight-error query-str at)])
      [error-panel
       :status code
       :content [:div message]])))

(defn no-results-panel [query-str]
  (fn [query-str]
    [error-panel :status (format "Ooops! No matches found for query: %s" query-str)]))

(defn results-frame []
  (let [status (re-frame/subscribe [:project-session :status])
        query-size (re-frame/subscribe [:project-session :query :results-summary :query-size])
        query-str (re-frame/subscribe [:project-session :query :results-summary :query-str])
        throbbing? (re-frame/subscribe [:throbbing? :results-frame])]
    (fn []
      (let [{:keys [status content]} @status]
        (cond
          @throbbing?         [throbbing-panel]
          (= :error status)   [error-panel-by-type content]
          (zero? @query-size) [no-results-panel @query-str]
          :else               [results-table])))))

(defn query-frame-spacer []
  [:div.row {:style {:margin-top "5px"}}])

(defn animate [throbbing? progress? value {:keys [max-val fps increase] :as opts}]
  (if (< @value max-val)
    (let [increase (if @throbbing? increase (* 2 increase))]
      (do (js/setTimeout
           #(animate throbbing? progress? value (update-in opts :increase (partial * 2)))
           fps)
          (swap! value + increase)))
    (do (reset! progress? false) (reset! value 0))))

(defn progress-bar
  [throbbing? & {:keys [fps max-val increase] :or {fps 15 max-val 100 increase 2}}]
  (let [value (reagent/atom 0)]
    (fn [throbbing? & opts]
      (let [progress? (reagent/atom @throbbing?)]
        (animate throbbing? progress? value opts)
        (if-not @progress?
          [:hr]
          [:progress {:max (str max-val) :value (str @value)}])))))

(defn query-frame []
  (let [has-query? (re-frame/subscribe [:has-query-results?])
        fetching-annotations? (re-frame/subscribe [:throbbing? :fetch-annotations])]
    (fn []
      [:div.container-fluid
       [query-toolbar]
       [query-frame-spacer]
       (when @has-query? [sort-toolbar])
       (when @has-query? [progress-bar fetching-annotations?]) ;still not quite working
       (when @has-query? [results-toolbar])
       [query-frame-spacer]
       [results-frame]])))

(defn label-closed-header [label]
  (fn []
    [:div.container-fluid [:div.row [:div.col-lg-10 [:div label]]]]))

(defn query-panel-closed-header []
  (let [query-str (re-frame/subscribe [:project-session :query :results-summary :query-str])
        query-size (re-frame/subscribe [:project-session :query :results-summary :query-size])]
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

(defn unmark-all-hits-btn []
  [bs/button
   {:onClick #(re-frame/dispatch [:unmark-all-hits])
    :style {:font-size "12px" :height "34px"}}
   "Unmark hits"])

(defn annotation-open-header []
  (fn []
    [:div.container-fluid
     [:div.row
      [:div.col-lg-7.col-sm-5 [:div.pull-left [filter-annotation-buttons]]]
      [:div.col-lg-4.col-sm-5 [:div.pull-right [unmark-all-hits-btn]]]]]))

(defn minimizable-query-frame []
  [minimize-panel
   {:child query-frame
    :id "query-frame"
    :open-header (label-closed-header "Query Panel")
    :closed-header query-panel-closed-header}])

(defn minimizable-annotation-panel []
  (let [marked-hits (re-frame/subscribe [:marked-hits {:has-marked? false}])]
    (when-not (zero? (count @marked-hits))
      [minimize-panel
       {:child annotation-panel
        :id "annotation-panel"
        :closed-header annotation-closed-header
        :open-header annotation-open-header
        :init true}])))

(defn query-panel []
  (let [panel-order (re-frame/subscribe [:project-session :components :panel-order])
        throbbing? (re-frame/subscribe [:throbbing? :results-frame])]
    (fn []
      [:div.container-fluid.pad
       {:style {:width "100%" :padding "0px 10px 0px 10px"}}
       (if (= (first @panel-order) "annotation-panel")
         [:div.row [minimizable-annotation-panel]]
         [:div.row [minimizable-query-frame]])
       (if (= (first @panel-order) "annotation-panel")
         [:div.row [minimizable-query-frame]]
         [:div.row [minimizable-annotation-panel]])
       [snippet-modal]])))
