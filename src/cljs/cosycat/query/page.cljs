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
            [cosycat.query.components.annotate-query-modal :refer [annotate-query-modal]]
            [cosycat.annotation.components.annotation-component
             :refer [annotation-component hit-row]]
            [cosycat.components :refer
             [error-panel throbbing-panel filter-annotation-buttons]]
            [taoensso.timbre :as timbre]))

;;; Query frame
(defn parse-query-error-msg [message]
  (let [re #"Query: (.+) ; has error at position: (\d+)"
        [_ query-str at] (first (re-seq re message))]
    [query-str (->int at)]))

(defn error-panel-by-type [content]
  (fn [{:keys [message code]}]
    (if (= code "Query string error")
      (let [[query-str at] (parse-query-error-msg message)]
        [error-panel
         {:status (str "Query misquoted starting at position " at)
          :content (highlight-error query-str at)}])
      [error-panel
       {:status code
        :content [:div message]}])))

(defn no-results-panel [query-str]
  (fn [query-str]
    [error-panel {:status (format "Ooops! No matches found for query: %s" query-str)}]))

(defn results-frame []
  (let [status (re-frame/subscribe [:project-session :status])
        query-size (re-frame/subscribe [:project-session :query :results :results-summary :query-size])
        query-str (re-frame/subscribe [:project-session :query :results :results-summary :query-str])
        throbbing? (re-frame/subscribe [:throbbing? :results-frame])]
    (fn []
      (let [{:keys [status content]} @status]
        (cond
          @throbbing?         [throbbing-panel :throbber :horizontal-loader]
          (= :error status)   [error-panel-by-type content]
          (zero? @query-size) [no-results-panel @query-str]
          :else               [results-table])))))

(defn animate [throbbing? progress? value {:keys [max-val fps increase] :as opts}]
  (if (< @value max-val)
    (let [increase (if @throbbing? increase (* 2 increase))]
      (do (js/setTimeout
           #(animate throbbing? progress? value (update-in opts :increase (partial * 2)))
           fps)
          (swap! value + increase)))
    (do (reset! progress? false) (reset! value 0))))

(defn progress-bar
  [throbbing? & {:keys [fps max-val increase] :or {fps 15 max-val 100 increase 2} :as opts}]
  (let [value (reagent/atom 0)]
    (fn [throbbing? & opts]
      (let [progress? (reagent/atom @throbbing?)]
        (animate throbbing? progress? value opts)
        (if-not @progress?
          [:hr]
          [:progress {:max (str max-val) :value (str @value)}])))))

(defn query-frame []
  (let [has-query-results? (re-frame/subscribe [:has-query-results?])
        has-query? (re-frame/subscribe [:has-query?])
        fetching-annotations? (re-frame/subscribe [:throbbing? :fetch-annotations])]
    (fn []
      [:div.container-fluid
       [query-toolbar]
       [:div.row {:style {:margin-top "5px"}}]
       (when @has-query? [sort-toolbar])
       (when @has-query-results? [progress-bar fetching-annotations?])
       (when @has-query-results? [results-toolbar])
       [:div.row {:style {:margin-top "5px"}}]
       [results-frame]])))

(defn query-frame-open-header []
  [:div.container-fluid [:div.row [:div.col-lg-10 [:span.pull-left [:h4 "Query Panel"]]]]])

(defn query-frame-closed-header []
  (let [path-to-results [:project-session :query :results]
        query-str (re-frame/subscribe (into path-to-results [:results-summary :query-str]))
        query-size (re-frame/subscribe (into path-to-results [:results-summary :query-size]))]
    (fn []
      [:div.container-fluid
       [:div.row
        [:div.col-lg-10
         [:span.truncate
          "Showing: " [:strong @query-size]
          " results for query: " [:code @query-str]]]]])))

(defn minimizable-query-frame []
  [minimize-panel
   {:child query-frame
    :id :query-frame
    :open-header query-frame-open-header
    :closed-header query-frame-closed-header}])

;;; annotation frame
(defn closed-annotation-component [hit-map color-map]
  (fn [hit-map color-map]
    [bs/table
     {:id "table-annotation"}
     [:thead]
     [:tbody
      [hit-row hit-map color-map
       :editable? true
       :show-match? true
       :show-hit-id? true]]]))

(defn annotation-frame [& {:keys [page-size] :or {page-size 10}}]
  (let [marked-hits (re-frame/subscribe [:marked-hits {:has-marked? false}])
        color-map (re-frame/subscribe [:filtered-users-colors])
        current-hit-page (re-frame/subscribe [:project-session :components :current-hit-page])
        open-hits (re-frame/subscribe [:project-session :components :open-hits])]
    (fn [& {:keys [page-size] :or {page-size 10}}]
      [:div.container-fluid
       (let [start (* page-size (or @current-hit-page 0))
             end (min (count @marked-hits) (+ start page-size))
             sorted-marked-hits (sort-by #(get-in % [:meta :num]) @marked-hits)]
         (doall (for [{hit-id :id :as hit-map} (subvec (vec sorted-marked-hits) start end)]
                  ^{:key (str hit-id)}
                  [:div.row
                   (if (contains? @open-hits hit-id)
                     [annotation-component hit-map color-map]
                     [closed-annotation-component hit-map color-map])])))])))

(defn annotation-closed-header []
  (let [marked-hits (re-frame/subscribe [:marked-hits {:has-marked? false}])]
    (fn []
      [:div.container-fluid
       [:div.row
        [:div.col-lg-10
         [:h5
          (str "Annotation panel (" (count @marked-hits) " selected hits)")]]]])))

(defn close-hits-btn []
  [bs/button
   {:onClick #(re-frame/dispatch [:close-hits])
    :style {:font-size "12px" :height "34px"}}
   "Close"])

(defn open-hits-btn []
  [bs/button
   {:onClick #(re-frame/dispatch [:open-hits])
    :style {:font-size "12px" :height "34px"}}
   "Open"])

(defn marked-hits-pager [& {:keys [page-size] :or {page-size 10}}]
  (let [current-hit-page (re-frame/subscribe [:project-session :components :current-hit-page])
        marked-hits (re-frame/subscribe [:marked-hits {:has-marked? false}])]
    (fn []
      [bs/pagination
       {:style {:margin "0px"}
        :next true
        :prev true
        :first true
        :last true
        :ellipsis false
        :boundaryLinks true
        :items (.ceil js/Math (/ (count @marked-hits) page-size))
        :maxButtons 5
        :activePage (if @current-hit-page (inc @current-hit-page) 1)
        :onSelect #(this-as this
                     (re-frame/dispatch
                      [:set-project-session-component [:current-hit-page]
                       (dec (.-eventKey this))]))}])))

(defn hits-toolbar []
  [bs/button-group
   [open-hits-btn]
   [close-hits-btn]])

(defn annotation-open-header []
  (fn []
    [:div.container-fluid
     [:div.row
      [:div.col-lg-5.col-sm-5 [:div.pull-left [filter-annotation-buttons]]]
      [:div.col-lg-3.col-sm-3 [:div.pull-right [hits-toolbar]]]
      [:div.col-lg-3.col-sm-3 [:div.pull-right [marked-hits-pager]]]]]))

(defn minimizable-annotation-frame []
  (let [marked-hits (re-frame/subscribe [:marked-hits {:has-marked? false}])]
    (when-not (zero? (count @marked-hits))
      [minimize-panel
       {:child annotation-frame
        :id :annotation-frame
        :closed-header annotation-closed-header
        :open-header annotation-open-header
        :init true}])))

(defn query-panel []
  (let [query-frame-open? (re-frame/subscribe [:project-session :components :panel-open :query-frame])
        throbbing? (re-frame/subscribe [:throbbing? :results-frame])]
    (fn []
      [:div.container-fluid.pad
       {:style {:width "100%" :padding "0px 10px 0px 10px"}}
       ;; show open panel first
       (if @query-frame-open?
         [:div
          [:div.row [minimizable-query-frame]]
          [:div.row [minimizable-annotation-frame]]]
         [:div
          [:div.row [minimizable-annotation-frame]]
          [:div.row [minimizable-query-frame]]])
       [snippet-modal]
       [annotate-query-modal]])))
