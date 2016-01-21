(ns cleebo.pages.query
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [re-com.core :as re-com]
            [cleebo.query-check :as q]
            [ajax.core :refer [GET]]
            [goog.string :as gstr]
            [goog.dom.dataset :as gdataset]
            [goog.dom.classes :as gclass]
            [goog.style :as gstyle]
            [goog.fx.dom :as gfx]
            [goog.fx.easing :as gfx-easing]
            [taoensso.timbre :as timbre])
  (:require-macros [cleebo.env :as env :refer [cljs-env]])
  (:import [goog.fx Animation]))

(def css-transition-group
  (reagent/adapt-react-class js/React.addons.CSSTransitionGroup))

(def corpora
  (let [{cqp-corpora :corpora} (cljs-env :cqp)
        {bl-corpora :corpora}  (cljs-env :blacklab)]
    (concat cqp-corpora bl-corpora)))

(defn by-id [id]
  (.-value (.getElementById js/document id)))

(defn pager-next
  ([size page-size] (pager-next size page-size 0))
  ([size page-size from]
   (let [to (+ from page-size)]
     (cond
       (= from size) [0 (min page-size size)]
       (>= to size)  [from size]
       :else         [from to]))))

(defn pager-prev
  ([size page-size] (pager-prev size page-size 0))
  ([size page-size from]
   (let [new-from (- from page-size)]
     (cond (zero? from)     [(- size page-size) size]
           (zero? new-from) [0 page-size]
           (neg?  new-from) [0 (+ new-from page-size)]
           :else            [new-from from]))))

(defn error-panel [{:keys [status status-text]}]
  [re-com/v-box
   :align :center
   :padding "40px"
   :gap "10px"
   :children
   [[:h3 [:span.text-muted status]]
    [:br]
    status-text]])

(defn error-handler [{:keys [status status-text]}]
  (re-frame/dispatch
   [:set-session [:query-results :status] {:status status :status-text status-text}]))

(defn query-results-handler [data]
  (let [{query-size :query-size} data
        data (if (zero? query-size) (assoc data :results nil) data)]
    (re-frame/dispatch [:set-query-results data])
    (re-frame/dispatch [:stop-throbbing :results-frame])))

(defn query
  "will need to support 'from' for in-place query-opts change"
  [{:keys [query-str corpus context size from] :or {from 0} :as query-args}]
  (GET "/query"
       {:handler query-results-handler
        :error-handler error-handler
        :params {:query-str query-str
                 :corpus corpus
                 :context context
                 :from from
                 :size size}}))

(defn query-range [{:keys [corpus from to context sort-map]}]
  (GET "/range"
       {:handler query-results-handler
        :error-handler error-handler
        :params {:corpus corpus
                 :from from
                 :to to
                 :context context
                 :sort-map sort-map}}))

(defn query-field []
  (let [query-opts (re-frame/subscribe [:query-opts])]
    (fn []
      [re-com/h-box
       :justify :between
       :children
       [[:h4 [:span.text-muted {:style {:line-height "15px"}} "Query Panel"]]
        [:div.form-group.has-feedback
         [:input#query-str.form-control
          {:style {:width "640px"}
           :type "text"
           :name "query"
           :placeholder "Example: [pos='.*\\.']" ;remove?
           :autocorrect "off"
           :autocapitalize "off"
           :spellcheck "false"
           :on-key-press
           (fn [k] (if (= (.-charCode k) 13)
                     (let [query-str (by-id "query-str")
                           arg-map (assoc @query-opts :query-str query-str)]
                       (re-frame/dispatch [:start-throbbing :results-frame])
                       (timbre/debug (q/parse-checks (q/check-query query-str q/check-fn-map)))
                       (query arg-map))))}
          [:i.zmdi.zmdi-search.form-control-feedback
           {:style {:font-size "1.75em" :line-height "35px"}}]]]]])))

(defn dropdown-opt [& {:keys [k placeholder choices width] :or {width "175px"}}]
  {:pre [(and k placeholder choices)]}
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
   :gap "5px"
   :children
   [[dropdown-opt
     :k :corpus
     :placeholder "Corpus: "
     :choices (map (partial hash-map :id) corpora)
     :width "225px"]
    [dropdown-opt
     :k :size
     :placeholder "Page size: "
     :choices (map (partial hash-map :id)
                   [5 10 15 25 35
                    55 85 125 190
                    290 435 655 985
                    1475 2215 3325 4985
                    7480 11220 16830])]
    [dropdown-opt
     :k :context
     :placeholder "Window size: "
     :choices (map (partial hash-map :id) (range 1 10))]]])

(defn nav-button [pager-fn label]
  (let [query-opts (re-frame/subscribe [:query-opts])
        query-results (re-frame/subscribe [:query-results])]
    (fn []
      [re-com/button
       :style {:font-size "12px" :height "34px"}
       :label label
       :on-click #(let [{:keys [query-size from to]} @query-results
                        {:keys [corpus context size]} @query-opts
                        [from to] (pager-fn query-size size from to)]
                    (re-frame/dispatch [:start-throbbing :results-frame])
                    (query-range {:corpus corpus
                                  :from from
                                  :to to
                                  :context context}))])))

(defn bordered-input [& {:keys [label model on-change on-key-press]}]
  {:pre [(and label model)]}
  (let [inner-value (reagent/atom "")]
    (fn [& {:keys [label model-fn on-change on-key-press]
            :or {on-change identity on-key-press identity}}]
      [re-com/h-box
       :children
       [[:div
         {:style {:font-size "12px"
                  :height "34px"
                  :line-height "25px"
                  :padding "0 0.3em"
                  :border "1px solid #ccc"
                  :border-right "none"
                  :border-radius "4px 0 0 4px"
                  :background-color "#EDEDED"
                  :position "static"
                  :margin "auto"}}
         label]
        [:input
         {:style {:width "3.7em"
                  :padding-left "0.3em"
                  :padding-top "2px"
                  :border "1px solid #ccc"
                  :border-radius "0 4px 4px 0"
                  :border-left "none"}
          :type "number"
          :min "1"
          :default-value model
          :on-change #(on-change (reset! inner-value (.-value (.-target %))))
          :on-key-press #(on-key-press % @inner-value)}]]])))

(defn nav-buttons []
  (let [query-opts (re-frame/subscribe [:query-opts])
        query-results (re-frame/subscribe [:query-results])
        criterion (reagent/atom "match")     ;defaults
        prop-name (reagent/atom "word")]
    (fn []
      [re-com/h-box
       :gap "15px"
       :children
       [[re-com/h-box
         :gap "0px"
         :children
         [[nav-button
           (fn [query-size size from to] (pager-prev query-size size from))
           [:div [:i.zmdi.zmdi-arrow-left {:style {:margin-right "10px"}}] "prev"]] 
          [nav-button
           (fn [query-size size from to] (pager-next query-size size to))
           [:div "next" [:i.zmdi.zmdi-arrow-right {:style {:margin-left "10px"}}]]]
          [bordered-input
           :label "go->"
           :model (inc (:from @query-results))
           :on-key-press
           (fn [k value]
             (if (= (.-charCode k) 13)
               (let [{:keys [corpus context size]} @query-opts
                     {:keys [query-size]} @query-results
                     from-hit (max 0 (min (dec (js/parseInt value)) query-size))]
                 (re-frame/dispatch [:start-throbbing :results-frame])
                 (query-range
                  {:corpus corpus
                   :from from-hit
                   :to (+ from-hit size)
                   :context context}))))]]]
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
           :disabled? (let [{:keys [corpus]} @query-opts]
                        (not (some #{corpus} (:corpora (cljs-env :blacklab)))))
           :on-click
           (fn []
             (let [{:keys [query-size from to]} @query-results
                   {:keys [corpus context size]} @query-opts]
               (re-frame/dispatch [:start-throbbing :results-frame])
               (query-range {:corpus corpus
                             :from from
                             :to (+ from size)
                             :context context
                             :sort-map {:criterion @criterion
                                        :prop-name @prop-name}})))]
          [re-com/button
           :style {:font-size "12px" :height "34px"}
           :label "Sort all"
           :disabled? (let [{:keys [corpus]} @query-opts]
                        (not (some #{corpus} (:corpora (cljs-env :blacklab)))))
           :on-click
           (fn []
             (let [{:keys [query-size from to]} @query-results
                   {:keys [corpus context size]} @query-opts]
               (re-frame/dispatch [:start-throbbing :results-frame])
               (query-range {:corpus corpus
                             :from from
                             :to (+ from size)
                             :context context
                             :sort-map {:criterion @criterion
                                        :prop-name @prop-name
                                        :sort-type "all"}})))]]]]])))

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
        [query-opts-menu]]])))

(defn throbbing-panel []
  [re-com/box
   :align :center
   :justify :center
   :padding "50px"
   :child [re-com/throbber :size :large]])

(defn result-by-id [e results-map]
  (let [id (gdataset/get (.-currentTarget e) "id")
        hit (get-in results-map [(js/parseInt id) :hit])]
    id))

(defn fade-out [e]
  (let [anim (gfx/FadeOut. (.-currentTarget e) 1 0.5 5200 gfx-easing/easeOut)]
    (.play anim)))

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
              (gclass/enable e "highlighted" @highlighted?)
              (update-selection selection (gdataset/get e "id") @highlighted?)))
         :on-mouse-up #(swap! mouse-down? not) }
        [:thead]
        [:tbody {:style {:font-size "11px"}}
         (for [[i {:keys [hit meta]}] (sort-by first (:results @query-results))]
           ^{:key i}
           [:tr
            {:data-num i}
            (into
             ^{:key (str  i)} [:td (inc i)]
             (for [{:keys [id word] :as token} hit]
               (cond
                 (:match token) ^{:key (str i "-" id)} [:td.info {:data-id id} word]
                 :else          ^{:key (str i "-" id)} [:td {:data-id id} word])))])]]])))

(defn results-frame [selection]
  (let [status (re-frame/subscribe [:session :query-results :status])
        query-size (re-frame/subscribe [:session :query-results :query-size])
        throbbing? (re-frame/subscribe [:throbbing? :results-frame])]
    (fn [selection]
      (let [{:keys [status status-text]} @status]
        (cond
          @throbbing?       (throbbing-panel)
          (= status :error) [error-panel
                             {:status "Ups! something bad happened" :status-text status-text}]
          (= 0 @query-size) [error-panel
                             {:status "The query returned no matching results"}]
          :else             [table-results selection])))))

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
    [query-main]]])
