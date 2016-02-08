(ns cleebo.pages.query
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [re-com.core :as re-com]
            [cleebo.logic.query :as q]
            [cleebo.query-parser :refer [missing-quotes]]            
            [cleebo.utils :refer [notify! by-id normalize-from]]            
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
  [:div.container-fluid
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

(defn query-field []
  (let [query-opts (re-frame/subscribe [:query-opts])]
    (fn []
      [:div.row
       [:div.col-lg-4
        [:h4 [:span.text-muted {:style {:line-height "15px"}} "Query Panel"]]]
       [:div.col-lg-8
        [:div.form-group.has-feedback
         [:input#query-str.form-control
          {:style {:width "100%"}
           :type "text"
           :name "query"
           :placeholder "Example: [pos='.*\\.']" ;remove?
           :autocorrect "off"
           :autocapitalize "off"
           :spellcheck "false"
           :on-key-press
           (fn [k]
             (let [query-str (normalize-str (by-id "query-str"))]
               (if (and (not (zero? (count query-str))) (= (.-charCode k) 13))
                 (let [{status :status at :at} (missing-quotes query-str)
                       at (+ at (empty-before query-str at))
                       args-map (assoc @query-opts :query-str query-str)]
                   (case status
                     :mismatch
                     (re-frame/dispatch
                      [:set-session [:query-results :status]
                       {:status :query-str-error
                        :status-content {:query-str query-str :at at}}])
                     :finished
                     (do (re-frame/dispatch [:start-throbbing :results-frame])
                         (q/query args-map)))))))}
          [:i.zmdi.zmdi-search.form-control-feedback
           {:style {:font-size "1.75em" :line-height "35px"}}]]]]])))

(defn dropdown-select [& {:keys [init-label choices label-fn click-fn]}]
  {:pre [(and init-label choices)]}
  (let [label (reagent/atom init-label)]
    (fn [& {:keys [init-label choices label-fn click-fn]}]
      [bs/split-button {:title @label
                        :onClick #(click-fn (.-textContent (.-target %)))
                        :bsStyle "info"
                        :onSelect (fn [e k] (label-fn k label))}
       (for [{:keys [key label]} choices]
         ^{:key key} [bs/menu-item {:eventKey label} label])])))

(defn query-opts-menu []
  (let [->map (fn [k l] {:key k :label l})]
    [bs/button-group
     [dropdown-select
      :init-label "Corpus"
      :choices (mapv #(->map % %) corpora)]
     [dropdown-select
      :init-label "Page size"
      :choices (map #(->map % %) [5 10 15 25 35 55 85 125 190 290 435 655 985])]
     [dropdown-select
      :init-label "Window size"
      :choices (map #(->map % %) (range 1 10))]]))

(defn nav-button [pager-fn label]
  (let [query-opts (re-frame/subscribe [:query-opts])
        query-results (re-frame/subscribe [:query-results])]
    (fn []
      [re-com/button
       :style {:font-size "12px" :height "34px"}
       :label label
       :on-click #(let [{:keys [size corpus context]} @query-opts
                        {:keys [query-size from to]} @query-results
                        [from to] (pager-fn query-size size from to)]
                    (q/query-range corpus from to context))])))

(defn bordered-input [& {:keys [label model on-change on-key-press]}]
  {:pre [(and label model)]}
  (let [inner-value (reagent/atom "")]
    (fn [& {:keys [label model-fn on-change on-key-press]
            :or {on-change identity on-key-press identity}}]
      [re-com/h-box
       :children
       [[:div.bordered-input label]
        [:input.bordered-input
         {:type "number"
          :min "1"
          :default-value model
          :on-change #(on-change (reset! inner-value (.-value (.-target %))))
          :on-key-press #(on-key-press % @inner-value)}]]])))

(defn nav-buttons []
  (let [query-opts (re-frame/subscribe [:query-opts])
        query-results (re-frame/subscribe [:query-results])
        criterion (reagent/atom "match")
        prop-name (reagent/atom "word")]
    (fn []
      [re-com/h-box
       :gap "15px"
       :children
       [[re-com/h-box
         :gap "0px"
         :children
         [[nav-button
           (fn [query-size size from to] (q/pager-prev query-size size from))
           [:div [:i.zmdi.zmdi-arrow-left {:style {:margin-right "10px"}}] "prev"]] 
          [nav-button
           (fn [query-size size from to] (q/pager-next query-size size to))
           [:div "next" [:i.zmdi.zmdi-arrow-right {:style {:margin-left "10px"}}]]]
          [bordered-input
           :label "go->"
           :model (inc (:from @query-results))
           :on-key-press
           (fn [k value]
             (if (= (.-charCode k) 13)
               (let [{:keys [query-size]} @query-results
                     {:keys [corpus context size]} @query-opts
                     from (normalize-from (dec (js/parseInt value)) query-size)]
                 (q/query-range corpus from (+ from size) context))))]]]
        [re-com/h-box
         :gap "0px"
         :children
         [[re-com/single-dropdown
           :width "155px"
           :choices [{:id "match"}
                     {:id "left-context"}
                     {:id "right-context"}]
           :label-fn #(str "sort by: " (:id %))
           :model criterion
           :on-change (partial reset! criterion)]
          [re-com/single-dropdown
           :width "185px"
           :choices [{:id "word"} {:id "pos"} {:id "lemma"}]
           :label-fn #(str "sort prop: " (:id %))
           :model prop-name
           :on-change (partial reset! prop-name)]
          [re-com/button
           :style {:font-size "12px" :height "34px"}
           :label "Sort page"
           :disabled? (not (some #{(:corpus @query-opts)} (:corpora (cljs-env :blacklab))))
           :on-click #(let [{:keys [corpus context size]} @query-opts
                            {:keys [from]} @query-results]
                        (q/query-sort corpus from (+ from size) context
                                      @criterion @prop-name :sort-range))]
          [re-com/button
           :style {:font-size "12px" :height "34px"}
           :label "Sort all"
           :disabled? (not (some #{(:corpus @query-opts)} (:corpora (cljs-env :blacklab))))
           :on-click #(let [{:keys [corpus context size]} @query-opts
                            {:keys [from]} @query-results]
                        (q/query-sort corpus from (+ from size) context
                                      @criterion @prop-name :sort-query))]]]]])))

(defn query-result-label [{:keys [from to query-size]}]
  (fn [{:keys [from to query-size]}]
    [re-com/label
     :style {:line-height "30px"}
     :label (let [from (inc from) to (min to query-size)]
              (gstr/format "Displaying %d-%d of %d hits" from to query-size))]))

(defn toolbar []
  (let [query-results (re-frame/subscribe [:query-results])]
    (fn []
      [re-com/h-box
       :justify :between
       :align :end
       :gap "5px"
       :children
       [[re-com/h-box
         :style {:visibility (if-not (:results @query-results) "hidden" "visible")}
         :gap "10px"
         :children
         [[query-result-label @query-results]
          [nav-buttons]]]
        ;[query-opts-menu]
        ]])))

(defn throbbing-panel []
  [re-com/box
   :align :center
   :justify :center
   :padding "50px"
   :child [re-com/throbber :size :large]])

(defn update-selection [selection id flag]
  (if flag
    (swap! selection assoc id true)
    (swap! selection dissoc id)))

(defn table-results [selection]
  (let [query-results (re-frame/subscribe [:query-results])
        mouse-down? (reagent/atom false)
        highlighted? (reagent/atom false)]
    (fn [selection]
      [re-com/box
       :child
       [:table#table1.table.table-results
        {:on-mouse-down
         #(let [e (aget % "target")
                button (aget % "button")]
            (.preventDefault %)         ;avoid text selection
            (when (zero? button)
              (swap! mouse-down? not)
              (gclass/toggle e "highlighted")
              (reset! highlighted? (gclass/has e "highlighted"))
              (update-selection selection (gdataset/get e "id") @highlighted?)))
         :on-mouse-over
         #(let [e (aget % "target")
                button (aget % "button")]
            (when (and (zero? button) @mouse-down?)
              (gclass/enable e "highlighted") @highlighted?
              (update-selection selection (gdataset/get e "id") @highlighted?)))
         :on-mouse-up #(swap! mouse-down? not) }
        [:thead]
        [:tbody {:style {:font-size "11px"}}
         (for [[i {:keys [hit meta]}] (sort-by first (:results @query-results))]
           ^{:key i}
           [:tr
            {:data-num i}
            (for [{:keys [id word] :as token} hit]
               (cond
                 (:match token) ^{:key (str i "-" id)} [:td.info {:data-id id} word]
                 :else          ^{:key (str i "-" id)} [:td {:data-id id} word]))
            ;; (into
            ;;  ^{:key (str  i)} [:td (inc i)]
            ;;  (for [{:keys [id xmlid word] :as token} hit]
            ;;    (cond
            ;;      (:match token) ^{:key (str i "-" xmlid)} [:td.info {:data-id xmlid} word]
            ;;      :else          ^{:key (str i "-" xmlid)} [:td {:data-id xmlid} word])))
            ])]]])))

(defn results-frame [selection]
  (let [status (re-frame/subscribe [:session :query-results :status])
        query-size (re-frame/subscribe [:session :query-results :query-size])
        throbbing? (re-frame/subscribe [:throbbing? :results-frame])]
    (fn [selection]
      (let [{:keys [status status-content]} @status]
        (cond
          @throbbing?                 (throbbing-panel)
          (= status :error)           [error-panel
                                       :status "Ups! something bad happened"
                                       :status-content [:div status-content]]
          (= 0 @query-size)           [error-panel
                                       :status "The query returned no matching results"]         
          (= status :query-str-error) [error-panel
                                       :status (str "Query is misquoted starting at position "
                                                    (inc (:at status-content)))
                                       :status-content (highlight-error status-content)]
          :else                       [table-results selection])))))

(defn annotation-frame [selection]
  (fn [selection]
    (let [style {:style {:position "fixed"
                         :width "100%"
                        ;:left "175px"                         
                         :bottom 0
                         :background-color "rgb(235, 240, 242)"}}]
      [:div style (keys @selection)])))

(defn query-main []
  (let [selection (reagent/atom {})]
    (fn []
      [re-com/v-box
       :align :stretch
       :gap "20px"
       :children
       [[toolbar]
        [results-frame selection]
        [annotation-frame selection]]])))

(defn query-panel []
  [re-com/v-box
   :style {:width "100%"}
   :children
   [[query-field]
    [query-opts-menu]
    [query-main]]])
