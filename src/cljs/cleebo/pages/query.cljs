(ns cleebo.pages.query
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [re-com.core :as re-com]
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

(defn input-select [& {:keys [init-label options label-fn target]}]
  {:pre [(and init-label options)]}
  (let [label (reagent/atom init-label)]
    (fn [& {:keys [init-label options label-fn target]}]
      [bs/input
       {:type "select"
;        :style {:width "150px"}
;        :value @label
;        :onChange #(.log js/console @label)
        :onSelect (fn [e k]
                    (re-frame/dispatch [:set-session [:query-opts target] k])
                    (label-fn k label))}
       (for [{:keys [key label]} options]
         ^{:key key} [:option {:value label} label])])))

(defn dropdown-select [{:keys [init-label options label-fn select-fn]}]
  (let [label (reagent/atom init-label)]
    (fn [{:keys [init-label options label-fn select-fn]}]
      [bs/dropdown-button
       {:title @label
        :onSelect (fn [e k] (do (select-fn k) (label-fn k label)))}
       (for [{:keys [key label]} options]
         ^{:key key} [bs/menu-item {:eventKey label} label])])))

(defn query-opts-menu [query-opts]
  (fn [query-opts]
    (let [{:keys [corpus context size]} @query-opts]
      [:div.row
       [:div.col-lg-4.pad
        [:div.container-fluid
         [:div.row
          [:div.col-lg-4.pad [bs/label "Corpus"]]
          [:div.col-lg-8.pad
           [input-select
            :init-label (str "Corpus: " corpus)
            :options (mapv #(->map % %) corpora)
            :label-fn (fn [k label] (reset! label (str "sort by: " k)))
            :target :corpus]]]]]
       [:div.col-lg-4.pad
        [:div.container-fluid
         [:div.row
          [:div.col-lg-4.pad [bs/label "Window size"]]
          [:div.col-lg-8.pad
           [input-select
            :init-label (str "Window size: " context)
            :options (map #(->map % %) (range 1 10))
            :label-fn (fn [k label] (reset! label (str "sort by: " k)))
            :target :context]]]]]
       [:div.col-lg-4.pad
        [:div.container-fluid
         [:div.row
          [:div.col-lg-4 [bs/label "Context"]]
          [:div.col-lg-8
           [input-select
            :init-label (str "Page size: " size)
            :options (map #(->map % %) [5 10 15 25 35 55 85 125 190 290 435 655 985])
            :label-fn (fn [k label] (reset! label (str "sort by: " k)))
            :target :size]]]]]])))

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
        {:init-label "sort by: "
         :options (map #(->map % %) ["match" "left-context" "right-context"])
         :label-fn (fn [k label] (reset! label (str "sort by: " k)))
         :select-fn (fn [k] (reset! criterion k))}]
       [dropdown-select
        {:init-label "sort prop"
         :options (map #(->map % %) ["word" "pos" "lemma"])
         :label-fn (fn [k _] (reset! prop-name k))}]
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
       [:div.row
        [:div.col-lg-6
         [:div.row
          [:div.col-lg-4.pad [query-result-label @query-results]]
          [:div.col-lg-8.pad [pager-buttons]]]]
        [:div.col-lg-6.pad [:div.pull-right [sort-buttons query-opts query-results]]]]])))

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
      [bs/table
       {:responsive true
        :className "table-results"
        :id "table"
        :on-mouse-down
        #(let [e (aget % "target")
               button (aget % "button")]
           (.preventDefault %)          ;avoid text selection
           (.log js/console button)
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
           ])]])))

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
                                       :status (str "Query misquoted starting at position "
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
      [:div.container-fluid
       [:div.row [toolbar]]
       [:br]
       [:div.row [results-frame selection]]
       [:div.row [annotation-frame selection]]])))

(defn query-panel []
  [:div.container
   {:style {:width "100%" :padding "0px"}}
   [:row [query-field]]
   [:row [query-main]]])
