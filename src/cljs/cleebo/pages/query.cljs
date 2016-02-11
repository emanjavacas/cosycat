(ns cleebo.pages.query
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [cleebo.logic.query :as q]
            [cleebo.query-parser :refer [missing-quotes]]            
            [cleebo.utils :refer [notify! by-id ->map normalize-from]]
            [goog.string :as gstr]
            [goog.events :as gevents]
            [goog.dom.dataset :as gdataset]
            [goog.dom.classes :as gclass]
            [taoensso.timbre :as timbre]
            [clojure.string :as str]
            [react-bootstrap.components :as bs]
            [cljsjs.react-bootstrap]
;            [material-ui.core :as ui :include-macros true]
            )
  (:require-macros [cleebo.env :as env :refer [cljs-env]]))

(def corpora
  (let [{cqp-corpora :corpora} (cljs-env :cqp)
        {bl-corpora :corpora}  (cljs-env :blacklab)]
    (concat cqp-corpora bl-corpora)))

(defn error-panel [& {:keys [status status-content]}]
  {:pre [(and status)]}
  [:div.container-fluid.text-center
   {:style {:padding "40px"}}
   [:div.row [:h3 [:span.text-muted status]]]
   [:div.row [:br]]
   [:div.row.text-center status-content]])

(defn highlight-n [s n]
  (let [pre (subs s 0 n)
        post (subs s (inc n))
        target [:span
                {:style {:background-color "rgba(255, 0, 0, 0.3)"}}
                (nth s n)]]
    [:tt.text-center pre target post]))

(defn replace-char [s n replacement]
  (let [pre (subs s 0 n)
        post (subs s (inc n))]
    (str pre replacement post)))

(defn nbsp [& [n]]
  (gstr/unescapeEntities "&nbsp;"))

(defn normalize-str [s]
  (str/replace s #"[ ]+" " "))

(defn empty-before [s n]
  (count (filter #(= % " ")  (subs s n))))

(defn highlight-error [{query-str :query-str at :at}]
  [:div
   {:style {:display "inline-block"}}
   [:div.alert.alert-danger
    {:style {:border-right "none"
             :color "#333"
             :background-color "rgba(255, 0, 0, 0.1)"
             :padding "0px"
             :border-left "4px solid rgba(255, 0, 0, 0.8)"
             :border-top "none"
             :border-radius "0px"
             :border-bottom "none"
             :margin "0px"}}
    (highlight-n query-str at)]
   [:tt.text-center
    {:style {:padding-left "3.5px"}}
    (replace-char
     (apply str (repeat (count query-str) (nbsp)))
     at
     (gstr/unescapeEntities "&#x21D1;"))]])

(defn dropdown-select [{:keys [label model options select-fn header]}]
  (let [local-label (reagent/atom model)]
    (fn [{:keys [label model options select-fn header] :or {select-fn identity}}]
      [bs/dropdown
       {:id "Dropdown"
        :onSelect (fn [e k] (reset! local-label k) (select-fn k))}
       [bs/button
        {:style {:pointer-events "none !important"}}
        [:span.text-muted label] @local-label]
       [bs/dropdown-toggle]
       [bs/dropdown-menu
        (concat
         [^{:key "header"} [bs/menu-item {:header true} header]
          ^{:key "divider"} [bs/menu-item {:divider true}]]
         (for [{:keys [key label]} options]
           ^{:key key} [bs/menu-item {:eventKey label} label]))]])))

(defn query-opts-menu [query-opts]
  (fn [query-opts]
    (let [{:keys [corpus context size]} @query-opts]
      [bs/button-toolbar
       [dropdown-select
        {:label "corpus: "
         :header "Select a corpus"
         :options (mapv #(->map % %) corpora)
         :model corpus
         :select-fn #(re-frame/dispatch [:set-session [:query-opts :corpus] %])}]
       [dropdown-select
        {:label "window: "
         :header "Select window size"
         :options (map #(->map % %) (range 1 10))
         :model context
         :select-fn #(re-frame/dispatch [:set-session [:query-opts :context] %])}]
       [dropdown-select
        {:label "size: "
         :header "Select page size"
         :options (map #(->map % %) [5 10 15 25 35 55 85 125 190 290 435 655 985])
         :model size
         :select-fn #(re-frame/dispatch [:set-session [:query-opts :size] %])}]])))

(defn query-logic [& {:keys [query-opts query-results]}]
  (fn [k]
    (let [query-str (normalize-str (by-id "query-str"))]
      (if (and (not (zero? (count query-str))) (= (.-charCode k) 13))
        (let [{status :status at :at} (missing-quotes query-str)
              at (+ at (empty-before query-str at))
              args-map (assoc @query-opts :query-str query-str)]
          (case status
            :mismatch (re-frame/dispatch
                       [:set-session [:query-results :status]
                        {:status :query-str-error
                         :status-content {:query-str query-str :at at}}])
            :finished (do (re-frame/dispatch [:start-throbbing :results-frame])
                          (q/query args-map))))))))

(defn query-field []
  (let [query-opts (re-frame/subscribe [:query-opts])]
    (fn []
      [:div.row
       [:div.col-lg-2
        [:h4 [:span.text-muted {:style {:line-height "15px"}} "Query Panel"]]]
       [:div.col-lg-10
        [:div.row
         [:div.col-lg-4
          [query-opts-menu query-opts]]
         [:div.col-lg-8
          [:div.input-group
           [:input#query-str.form-control
            {:style {:width "100%"}
             :type "text"
             :name "query"
             :placeholder "Example: [pos='.*\\.']" ;remove?
             :autoCorrect "off"
             :autoCapitalize "off"
             :spellCheck "false"
             :on-key-press (query-logic :query-opts query-opts)}]
           [:span.input-group-addon
            [bs/glyphicon {:glyph "search"}]]]]]]])))

(defn pager-button [pager-fn label]
  (let [query-opts (re-frame/subscribe [:query-opts])
        query-results (re-frame/subscribe [:query-results])]
    (fn []
      [bs/button
       {:onClick #(let [{:keys [size corpus context]} @query-opts
                        {:keys [query-size from to]} @query-results
                        [from to] (pager-fn query-size size from to)]
                    (q/query-range corpus from to context))
        :style {:font-size "12px" :height "34px"}}
       label])))

(defn pager-buttons []
  [bs/button-toolbar
   [pager-button
    (fn [query-size size from to] (q/pager-prev query-size size from))
    [:div [:i.zmdi.zmdi-arrow-left {:style {:margin-right "10px"}}] "prev"]] 
   [pager-button
    (fn [query-size size from to] (q/pager-next query-size size to))
    [:div "next" [:i.zmdi.zmdi-arrow-right {:style {:margin-left "10px"}}]]]
   [bs/split-button
    {:title "Go!"
     :role "menuitem"
     :onClick #(timbre/debug %)}]])

(defn sort-buttons [query-opts query-results]
  (let [criterion (reagent/atom "match")
        prop-name (reagent/atom "word")]
    (fn [query-opts query-results]
      [bs/button-toolbar
       {:justified true}
       [dropdown-select
        {:label "sort by: "
         :header "Select criterion"
         :model @criterion
         :options (map #(->map % %) ["match" "left-context" "right-context"])
         :select-fn (fn [k] (reset! criterion k))}]
       [dropdown-select
        {:label "sort prop: "
         :header "Select property"
         :options (map #(->map % %) ["word" "pos" "lemma"])
         :model @prop-name
         :select-fn (fn [k] (reset! prop-name k))}]
       [bs/button
        {:disabled (not (some #{(:corpus @query-opts)} (:corpora (cljs-env :blacklab))))
         :onClick #(let [{:keys [corpus context size]} @query-opts
                         {:keys [from]} @query-results]
                     (q/query-sort corpus from (+ from size) context
                                   @criterion @prop-name :sort-range))}
        "Sort page"]
       [bs/button
        {:disabled (not (some #{(:corpus @query-opts)} (:corpora (cljs-env :blacklab))))
         :onClick #(let [{:keys [corpus context size]} @query-opts
                         {:keys [from]} @query-results]
                     (q/query-sort corpus from (+ from size) context
                                   @criterion @prop-name :sort-query))}
        "Sort all"]])))

(defn query-result-label [{:keys [from to query-size]}]
  (fn [{:keys [from to query-size]}]
    [:label
     {:style {:line-height "35px"}}
     (let [from (inc from) to (min to query-size)]
       (gstr/format "Displaying %d-%d of %d hits" from to query-size))]))

(defn toolbar []
  (let [query-opts (re-frame/subscribe [:query-opts])
        query-results (re-frame/subscribe [:query-results])]
    (fn []
      [:div.container-fluid
       {:style {:visibility (if-not (:results @query-results) "hidden" "visible")}}
       [:div.row {:style {:margin-top "10px"}}
        [:div.col-lg-6
         [:div.row
          [:div.col-lg-4.pad [query-result-label @query-results]]
          [:div.col-lg-8.pad [pager-buttons]]]]
        [:div.col-lg-6.pad [:div.pull-right [sort-buttons query-opts query-results]]]]])))

(defn throbbing-panel []
  [:div.text-center
   [:div.loader]])

(defn update-selection [selection id flag]
  (if flag
    (swap! selection assoc id true)
    (swap! selection dissoc id)))

(defn annotate-line [line visible?]
  (let [data @(re-frame/subscribe [:session :query-results :results line])]
    (swap! visible? not)))

(defn table-results [selection visible?]
  (let [query-results (re-frame/subscribe [:query-results])
        mouse-down? (reagent/atom false)
        highlighted? (reagent/atom false)]
    (fn [selection visible?]
      [bs/table
       {:responsive true
        :className "table-results"
        :id "table"
        :on-mouse-down
        #(let [e (aget % "target")
               button (aget % "button")]
           (.preventDefault %)          ;avoid text selection
           (when (zero? button)
             (swap! mouse-down? not)
             (gclass/toggle e "highlighted")
             (reset! highlighted? (gclass/has e "highlighted"))
             (update-selection selection (gdataset/get e "id") @highlighted?)))
        :on-mouse-over
        #(let [e (aget % "target")
               button (aget % "button")]
           (when (and (zero? button) @mouse-down?)
             (gclass/enable e "highlighted" @highlighted?)
             (update-selection selection (gdataset/get e "id") @highlighted?)))
        :on-mouse-up #(swap! mouse-down? not)}
       [:thead]
       [:tbody {:style {:font-size "11px"}}
        (for [[i {:keys [hit meta]}] (sort-by first (:results @query-results))]
          ^{:key i}
          [:tr
           {:data-num i}
           (concat
            [^{:key (str i "ann")} [:td {:style {:width "20px" :background-color "#eeeeee"}}
                                    [bs/button
                                     {:bsSize "small"
;                                      :data-num i
                                      :onClick #(annotate-line i visible?)}
                                     [bs/glyphicon {:glyph "pencil"}]]]
             ^{:key (str i)} [:td  {:style {:width "20px" :background-color "#eeeeee"}}
                              [:label (inc i)]]]
            (for [{:keys [id xmlid word] :as token} hit]
              (cond
                (:match token) ^{:key (str i "-" id)} [:td.info {:data-id id} word]
                :else          ^{:key (str i "-" id)} [:td {:data-id id} word])))])]])))

(defn results-frame [selection visible?]
  (let [status (re-frame/subscribe [:session :query-results :status])
        query-size (re-frame/subscribe [:session :query-results :query-size])
        throbbing? (re-frame/subscribe [:throbbing? :results-frame])
        results (re-frame/subscribe [:session :query-results :results])]
    (fn [selection visible?]
      (let [{:keys [status status-content]} @status]
        (cond
          @throbbing?                 [throbbing-panel]
          (= status :error)           [error-panel
                                       :status "Ups! something bad happened"
                                       :status-content [:div status-content]]
          (= 0 @query-size)           [error-panel
                                       :status "The query returned no matching results"]
          (= status :query-str-error) [error-panel
                                       :status (str "Query misquoted starting at position "
                                                    (inc (:at status-content)))
                                       :status-content (highlight-error status-content)]
          (not (nil? @results))       [table-results selection visible?]
          :else                       [error-panel
                                       :status "No results to be shown. 
                                       Go do some research!"])))))

(defn annotation-frame [selection]
  (fn [selection]
    (let [style {:style {:position "fixed"
                         :width "100%"
                         :bottom 0
                         :background-color "rgb(235, 240, 242)"}}]
      [:div style (keys @selection)])))

(defn query-main [visible?]
  (let [selection (reagent/atom {})]
    (fn [visible?]
      [:div.container-fluid
       [:div.row [toolbar]]
       [:div.row [results-frame selection visible?]]
       [:div.row [annotation-frame selection]]])))

(defn query-panel [visible?]
  (fn [visible?]
    [:div.container
     {:style {:width "100%" :padding "0px"}}
     [:row [query-field]]
     [:row [query-main visible?]]]))
