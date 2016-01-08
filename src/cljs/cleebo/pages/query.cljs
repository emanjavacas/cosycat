(ns cleebo.pages.query
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [re-com.core :as re-com]
            [cleebo.ws :refer [send-transit-msg!]]
            [ajax.core :refer [GET]]
            [goog.string :as gstr]
            [taoensso.timbre :as timbre])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [re-com.core :refer [handler-fn]]))

(defn by-id [id]
  (.getElementById js/document id))

(defn error-handler [{:keys [status status-text]}]
 (.log js/console 
  (str "something bad happened: " status " " status-text)))

(defn pager-next
  ([size page-size] (pager-next size page-size 0))
  ([size page-size from]
   (let [to (+ from page-size)]
     (if (>= to size) 
       [from 0]
       [from to]))))

(defn pager-prev
  ([size page-size] (pager-prev size page-size 0))
  ([size page-size from]
   (let [new-from (- from page-size)]
     (cond (zero? from) [(- size page-size) size]
           (zero? new-from) [0 page-size]
           (neg?  new-from)  [0 (+ new-from page-size)]
           :else [new-from from]))))

(defn query
  "will need to support 'from' for in-place query-opts change"
  [{:keys [query-str corpus context size from] :or {from 0}}]
  (GET "/query"
       {:handler (fn [data] 
                   (timbre/debug data)
                   (re-frame/dispatch [:set-query-results data])
                   (re-frame/dispatch [:stop-throbbing :results-frame]))
        :error-handler error-handler
        :params {:query-str query-str
                 :corpus corpus
                 :context context
                 :from from
                 :size size}}))

(defn query-range [{:keys [corpus from to context]}]
  (GET "/range"
       {:handler (fn [data] 
                   (timbre/debug data)
                   (re-frame/dispatch [:set-query-results data])
                   (re-frame/dispatch [:stop-throbbing :results-frame]))
        :error-handler error-handler
        :params {:corpus corpus
                 :from from
                 :to to
                 :context context}}))

(defn query-field []
  (let [query-opts (re-frame/subscribe [:query-opts])
        query-str (re-frame/subscribe [:session :query-results :query-str])]
    (fn []
      [:div.container-fluid
       [:div.row
        [:div.col-sm-2
         [:h4 [:span.text-muted {:style {:line-height "15px"}} "Query Panel"]]]
        [:div.col-sm-10
         [:div.form-horizontal
          [:div.form-group.has-feedback
           [:input.form-control
            {:type "text"
             :name "query"
             :id "query-str"
             :placeholder (or @query-str "Example: [pos='.*\\.']") ;remove?
             :autocorrect "off"
             :autocapitalize "off"
             :spellcheck "false"
             :on-key-press
             #(if (= (.-charCode %) 13)
                (let [query-str (.-value (by-id "query-str"))]
                  (re-frame/dispatch [:start-throbbing :results-frame])
                  (query (assoc @query-opts :query-str query-str :type "type"))))}
            [:i.zmdi.zmdi-search.form-control-feedback
             {:style {:font-size "1.75em" :line-height "35px"}}]]]]]]])))

(defn dropdown-opt [k placeholder choices & {:keys [width] :or {width "125px"}}]
  [re-com/single-dropdown
   :style {:font-size "12px"}
   :width width
   :placeholder placeholder
   :choices choices
   :model @(re-frame/subscribe [:session :query-opts k])
   :label-fn #(str placeholder (:id %))
   :on-change #(re-frame/dispatch [:set-session [:query-opts k] %])])

(defn query-opts-menu []
  [re-com/h-box
   :justify :end
   :gap "5px"
   :children
   [[dropdown-opt
     :corpus
     "Corpus: "
     [{:id "PYCCLE-ECCO"} {:id "PYCCLE-EBBO"} {:id "MBG-CORPUS"}]
     :width "175px"]
    [dropdown-opt
     :size
     "Page size: "
     (map (partial hash-map :id) (range 1 15))]
    [dropdown-opt
     :context
     "Window size: "
     (map (partial hash-map :id) (range 1 10))]]])

(defn nav-buttons []
  (let [query-opts (re-frame/subscribe [:query-opts])
        query-results (re-frame/subscribe [:query-results])]
    (fn []
      [re-com/h-box
       :children
       [[re-com/button
         :style {:font-size "12px"}
         :label
         [:div [:i.zmdi.zmdi-arrow-left {:style {:margin-right "10px"}}] "prev page"]
         :on-click #(let [{:keys [query-size from to]} @query-results
                          {:keys [corpus context size]} @query-opts
                          [from to] (pager-prev query-size size from)]
                      (re-frame/dispatch [:start-throbbing :results-frame])
                      (query-range {:corpus corpus
                                    :from from
                                    :to to
                                    :context context}))]
        [re-com/button
         :style {:font-size "12px"}
         :label
         [:div "next page" [:i.zmdi.zmdi-arrow-right {:style {:margin-left "10px"}}]]
         :on-click #(let [{:keys [query-size from to]} @query-results
                          {:keys [corpus context size]} @query-opts
                          [from to] (pager-next query-size size to)]
                      (re-frame/dispatch [:start-throbbing :results-frame])
                      (query-range {:corpus corpus
                                    :from from
                                    :to to
                                    :context context}))]]])))

(defn toolbar []
  (let [query-results (re-frame/subscribe [:query-results])]
    (fn []
      [re-com/h-box
       :justify :between
       :gap "10px"
       :children
       [[query-opts-menu]
        [re-com/h-box
         :justify :end
         :gap "15px"
         :children
         [[re-com/label     
           :label (let [{:keys [from to query-size]} @query-results]
                    (gstr/format "Displaying %d-%d from %d hits" from to query-size))
           :style {:line-height "30px"}]
          [nav-buttons]]]]])))

(defn throbbing-panel []
  [re-com/v-box
   :gap "50px"
   :justify :between
   :children
   [""
    [re-com/h-box
     :gap "25px"
     :justify :between
     :children
     [""
      [re-com/throbber :size :large]
      ""]]
    ""]])

(defn results-frame []
  (let [query-results (re-frame/subscribe [:query-results])
        throbbing? (re-frame/subscribe [:throbbing? :results-frame])]
    (fn []
      (if @throbbing?
        (throbbing-panel)
        (when (:results @query-results)
          [:div
           [toolbar]
           [:br]
           [:table.table.table-striped.table-results
            [:thead]
            [:tbody {:style {:font-size "11px"}}
             (for [[i row] (map-indexed vector (:results @query-results))]
               ^{:key i}
               [:tr 
                (for [{:keys [pos word id] :as token} row]
                  (if (:match token)
                    ^{:key (str i "-" id)} [:td.info word]
                    ^{:key (str i "-" id)} [:td word]))])]]])))))

(defn annotation-frame []
  [:div "annotation frame!!!"])

(defn query-main []
  (let [annotation? (atom true)]
    (fn []
      [re-com/v-box :gap "50px"
       :children 
       [[re-com/box :align :stretch :child [results-frame]]
        (when @annotation?
          [re-com/box :align :center :child [annotation-frame]])]])))

(defn query-panel []
  [:div
   [query-field]
   [query-main]])
