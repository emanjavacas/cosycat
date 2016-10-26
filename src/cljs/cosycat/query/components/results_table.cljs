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

(defn include-hit [hit-id query-id discarded?]
  (fn [hit-id query-id discarded?]
    (let [green "#5cb85c", red "#d9534f"]
      (if discarded?
        [bs/glyphicon
         {:glyph "remove-circle"
          :class "ignore"
          :style {:color red :cursor "pointer"}
          :onClick #(re-frame/dispatch [:query-remove-metadata hit-id query-id])}]
        [bs/glyphicon
         {:glyph "ok-circle"
          :class "ignore"
          :style {:color green :cursor "pointer"}
          :onClick #(re-frame/dispatch [:query-add-metadata hit-id query-id])}]))))

(defn results-row [hit-num {:keys [id]} {:keys [color-map token-field break active-query]}]
  (let [discarded? (re-frame/subscribe [:discarded-hit id])]
    (fn [hit-num {hit :hit id :id {:keys [num marked]} :meta} {:keys [color-map token-field break active-query]}]
      (let [row-class (merge-classes (when marked "marked") (when break "break"))
            background "#F9F9F9"]
        [:tr {:class row-class :data-hit id}
         (concat
          [^{:key (str hit-num "-check")}
           [:td.ignore
            {:class (if (:marked meta) "checked")
             :style {:width "20px"
                     :background-color background
                     :cursor "pointer"
                     :color (if (:marked meta) "#999999" "black")}}
            [:input.checkbox-custom.ignore
             {:id (str hit-num "-check")
              :type "checkbox"
              :checked marked
              :on-change #(re-frame/dispatch [:mark-hit {:hit-id id :flag (not marked)}])}]
            [:label.checkbox-custom-label.ignore
             {:for (str hit-num "-check")
              :tab-index (inc hit-num)}]]
           ;; discard
           (when active-query
             ^{:key (str hit-num "-dis")}
             [:td.ignore
              {:style {:background-color background :line-height "24px"}}
              [include-hit id active-query @discarded?]])
           ;; hit number
           ^{:key (str hit-num "-num")}
           [:td.ignore.snippet-trigger
            {:style {:width "20px"
                     :background-color background
                     :cursor "pointer"
                     :line-height "35px"
                     :padding "0px"}
             :on-double-click (on-double-click id)}
            [:label.ignore
             {:style {:font-weight "bold" :cursor "pointer"}}
             (inc (or num hit-num))]]]
          ;; hit
          (for [token hit]
            ^{:key (str hit-num "-" (:id token))} [hit-token token color-map token-field]))]))))

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
        active-query (re-frame/subscribe [:project-session :components :active-query])
        token-field (re-frame/subscribe [:project-session :components :token-field])
        mouse-down? (reagent/atom false)
        highlighted? (reagent/atom false)]
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
           ^{:key hit-num}
           [results-row hit-num hit-map
            {:color-map color-map
             :token-field @token-field
             :break break
             :active-query @active-query}]))]])))
