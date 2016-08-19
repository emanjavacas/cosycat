(ns cleebo.query.components.results-table
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [taoensso.timbre :as timbre]
            [goog.dom :as gdom]
            [goog.dom.classes :as gclass]
            [goog.dom.dataset :as gdataset]
            [react-bootstrap.components :as bs]
            [cleebo.utils :refer [->box]]))

(defn is-in-checked-hit?
  "is current cell child inside a checked hit row"
  [e]
  (gclass/has (gdom/getFirstElementChild (gdom/getParentElement e)) "checked"))

(defn get-hit-id
  "get hit id from hit row given a cell child"
  [e]
  (gdataset/get (gdom/getParentElement e) "hit"))

(defn on-mouse-down [mouse-down? highlighted?]
  (fn [event]
    (let [e (aget event "target"), btn (aget event "button")]
      (.preventDefault event)
      (when (and (zero? btn) (not (is-in-checked-hit? e)) (not (gclass/has e "ignore")))
        (gclass/toggle e "highlighted")
        (swap! mouse-down? not)
        (reset! highlighted? (gclass/has e "highlighted"))
        (re-frame/dispatch
         [(if @highlighted? :mark-token :unmark-token)
          {:hit-id (get-hit-id e)
           :token-id (gdataset/get e "id")}])))))

(defn on-mouse-over [mouse-down? highlighted?]
  (fn [event]
    (let [e (aget event "target"), btn (aget event "button")]
      (when (and (zero? btn)
                 @mouse-down?
                 (not (gclass/has e "ignore"))
                 (not (is-in-checked-hit? e)))
        (gclass/enable e "highlighted" @highlighted?)
        (re-frame/dispatch
         [(if @highlighted? :mark-token :unmark-token)
          {:hit-id (get-hit-id e)
           :token-id (gdataset/get e "id")}])))))

(defn on-mouse-up [mouse-down? highlighted?]
  (fn [event]
    (let [btn (aget event "button"), e (aget event "target")]
      (when (and (zero? btn) (not (gclass/has e "ignore")))
        (swap! mouse-down? not)))))

(defn highlight-annotation
  "if a given token has annotations it computes a color for the user with the most
  annotations in that token"
  [{anns :anns :as token} color-map]
  (let [filt-anns (filter #(contains? color-map (:username %)) (vals anns))
        [user _] (first (sort-by second > (frequencies (map :username filt-anns))))]
    (if-let [color (get color-map user)]
      (->box color))))

(defn hit-token [{:keys [id word match marked anns]} color-map]
  (fn [{:keys [id word match marked anns] :as token} color-map]
    (let [highlighted (if marked "highlighted " "")
          color (when anns (highlight-annotation token @color-map))
          is-match (when match "info")]
      [:td
       {:class (str highlighted is-match)
        :style {:box-shadow color}
        :data-id id}
       word])))

(defn on-double-click [hit-idx]
  (fn [event]
    (aset event "cancelBubble" true)
    (re-frame/dispatch [:fetch-snippet hit-idx])))

(defn results-row [hit-num {:keys [hit id meta]} color-map]
  (fn [hit-num {:keys [hit id meta]} color-map]
    [:tr {:data-hit id :style {:opacity (if (:marked meta) 0.6 1)}}
     (concat
      ;; checkbox
      [^{:key (str hit-num "-check")}
       [:td.ignore
        {:style {:width "20px" :background-color "#F9F9F9" :cursor "pointer"
                 :color (if (:marked meta) "#158CBA" "black")}
         :on-click #(re-frame/dispatch [:mark-hit {:hit-id id :flag (not (:marked meta))}])}
        [:i.zmdi.zmdi-edit.ignore]]
       ;; hit number
       ^{:key (str hit-num "-num")}
       [:td.ignore.snippet-trigger
        {:style {:width "20px" :background-color "#F9F9F9" :cursor "pointer"}
         :on-double-click (on-double-click id)}
        [:label.ignore
         {:style {:font-weight "bold" :cursor "pointer"}}
         (inc hit-num)]]]
      ;; hit
      (for [token hit]
        ^{:key (str hit-num "-" (:id token))} [hit-token token color-map]))]))

(defn results-table []
  (let [results (re-frame/subscribe [:results])
        from (re-frame/subscribe [:project-session :query :results-summary :from])
        mouse-down? (reagent/atom false)
        highlighted? (reagent/atom false)
        color-map (re-frame/subscribe [:filtered-users-colors])]
    (fn []
      [bs/table
       {:responsive true
        :striped true
        :id "table-results"
        :on-mouse-leave #(reset! mouse-down? false)
        :on-mouse-down (on-mouse-down mouse-down? highlighted?)        
        :on-mouse-over (on-mouse-over mouse-down? highlighted?)
        :on-mouse-up (on-mouse-up mouse-down? highlighted?)
        :tab-index 0
        :style {:border-collapse "collapse"}}
       [:thead]
       [:tbody {:style {:font-size "12px"}}
        (doall
         (for [[idx {:keys [hit meta id] :as hit-map}] (map-indexed vector @results)
               :let [hit-num (+ idx @from)]]
           ^{:key hit-num} [results-row hit-num hit-map color-map]))]])))
