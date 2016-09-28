(ns cosycat.query.components.results-table
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [taoensso.timbre :as timbre]
            [goog.dom :as gdom]
            [goog.dom.classes :as gclass]
            [goog.dom.dataset :as gdataset]
            [react-bootstrap.components :as bs]
            [cosycat.utils :refer [highlight-annotation merge-classes]]))

(defn is-in-checked-hit?
  "is current cell child inside a checked hit row"
  [e]
  (gclass/has (gdom/getFirstElementChild (gdom/getParentElement e)) "checked"))

(defn ignore-cell?
  [e]
  (or (gclass/has e "ignore") (is-in-checked-hit? e)))

(defn get-hit-id
  "get hit id from hit row given a cell child"
  [e]
  (gdataset/get (gdom/getParentElement e) "hit"))

(defn get-token-id
  [e]
  (gdataset/get e "id"))

(defn on-mouse-down [mouse-down? highlighted?]
  (fn [event]
    (let [e (aget event "target"), btn (aget event "button")]
      (.preventDefault event)
      (when (and (zero? btn) (not (ignore-cell? e)))
        (gclass/toggle e "highlighted")
        (swap! mouse-down? not)
        (reset! highlighted? (gclass/has e "highlighted"))
        (re-frame/dispatch
         [(if @highlighted? :mark-token :unmark-token)
          {:hit-id (get-hit-id e) :token-id (get-token-id e)}])))))

(defn on-mouse-over [mouse-down? highlighted?]
  (fn [event]
    (let [e (aget event "target"), btn (aget event "button")]
      (when (and @mouse-down? (zero? btn) (not (ignore-cell? e)))
        (gclass/enable e "highlighted" @highlighted?)
        (re-frame/dispatch
         [(if @highlighted? :mark-token :unmark-token)
          {:hit-id (get-hit-id e) :token-id (get-token-id e)}])))))

(defn on-mouse-up [mouse-down? highlighted?]
  (fn [event]
    (let [btn (aget event "button"), e (aget event "target")]
      (when (and (zero? btn) (not (ignore-cell? e)))
        (swap! mouse-down? not)))))

(defn hit-token [{:keys [id match marked anns]} color-map token-field]
  (fn [{:keys [id match marked anns] :as token-map} color-map token-field]
    (let [highlighted (if marked "highlighted " "")
          color (when anns (highlight-annotation token-map @color-map))
          is-match (when match "info")]
      [:td
       {:class (str highlighted is-match)
        :style {:box-shadow color}
        :data-id id}
       (get token-map token-field)])))

(defn on-double-click [hit-id]
  (fn [event]
    (.stopPropagation event)
    (re-frame/dispatch [:fetch-snippet hit-id])))

(defn results-row [hit-num hit-map {:keys [color-map token-field break]}]
  (fn [hit-num {hit :hit id :id {num :num marked :marked} :meta} {:keys [color-map token-field break]}]
    (let [row-class (merge-classes (when marked "marked") (when break "break"))]
      [:tr {:class row-class :data-hit id}
       (concat
        [^{:key (str hit-num "-check")}
         [:td.ignore
          {:class (if (:marked meta) "checked")
           :style {:width "20px"
                   :background-color "#F9F9F9"
                   :cursor "pointer"
                   :color (if (:marked meta) "#158CBA" "black")}}
          [:input.checkbox-custom.ignore
           {:id (str hit-num "-check")
            :type "checkbox"
            :checked marked
            :on-change #(re-frame/dispatch [:mark-hit {:hit-id id :flag (not marked)}])}]
          [:label.checkbox-custom-label.ignore
           {:for (str hit-num "-check")
            :tab-index (inc hit-num)}]]
         ;; hit number
         ^{:key (str hit-num "-num")}
         [:td.ignore.snippet-trigger
          {:style {:width "20px" :background-color "#F9F9F9" :cursor "pointer"}
           :on-double-click (on-double-click id)}
          [:label.ignore
           {:style {:font-weight "bold" :cursor "pointer"}}
           (inc (or num hit-num))]]]
        ;; hit
        (for [token hit]
          ^{:key (str hit-num "-" (:id token))} [hit-token token color-map token-field]))])))

(defn reduce-hits
  "returns hits indexed by `idx` and with a flag `break` indicating whether current hit belongs
   to different document than previous hit (as per `doc-id-field`)"
  [results & {:keys [doc-id-field] :or {doc-id-field :title}}]
  (reduce (fn [out [idx {{doc-id doc-id-field} :meta :as hit}]]
            (let [[_ {{prev-doc-id doc-id-field} :meta :as prev-meta} _] (last out)
                  break (and prev-meta (not= doc-id prev-doc-id))]
              (conj out [idx hit break])))
          []
          (map-indexed vector results)))

(defn results-table []
  (let [results (re-frame/subscribe [:results])
        from (re-frame/subscribe [:project-session :query :results-summary :from])
        color-map (re-frame/subscribe [:filtered-users-colors])
        token-field (re-frame/subscribe [:project-session :components :token-field])
        mouse-down? (reagent/atom false)
        highlighted? (reagent/atom false)]
    (timbre/debug @token-field)
    (fn []
      [bs/table
       {:responsive true
        :striped true
        :id "table-results"
        :on-mouse-leave #(reset! mouse-down? false)
        :on-mouse-down (on-mouse-down mouse-down? highlighted?)        
        :on-mouse-over (on-mouse-over mouse-down? highlighted?)
        :on-mouse-up (on-mouse-up mouse-down? highlighted?)
        :style {:border-collapse "collapse"}}
       [:thead]
       [:tbody {:style {:font-size "12px"}}
        (doall
         (for [[idx {:keys [hit meta id] :as hit-map} break] (reduce-hits @results)
               :let [hit-num (+ idx @from)]]
           ^{:key hit-num} [results-row hit-num hit-map
                            {:color-map color-map :token-field @token-field :break break}]))]])))
