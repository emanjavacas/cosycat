(ns cleebo.query.components.results-table
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [taoensso.timbre :as timbre]
            [goog.dom :as gdom]
            [goog.dom.classes :as gclass]
            [goog.dom.dataset :as gdataset]
            [react-bootstrap.components :as bs]
            [cleebo.utils :refer [highlight-annotation]]))

(defn is-in-checked-hit [e]
  (gclass/has (gdom/getFirstElementChild (gdom/getParentElement e)) "checked"))

(defn get-hit-id [e]
  (js/parseInt (gdataset/get (gdom/getParentElement e) "hit")))

(defn on-mouse-down [mouse-down? highlighted?]
  (fn [event]
    (let [e   (aget event "target")
          btn (aget event "button")]
      (.preventDefault event)                                 ;avoid text selection
      (when (and (zero? btn) (not (is-in-checked-hit e)) (not (gclass/has e "ignore"))) ;check btn type
        (gclass/toggle e "highlighted")
        (swap! mouse-down? not)
        (reset! highlighted? (gclass/has e "highlighted"))
        (re-frame/dispatch
         [:mark-token
          {:hit-id (get-hit-id e)
           :token-id (gdataset/get e "id")
           :flag @highlighted?}])))))

(defn on-mouse-over [mouse-down? highlighted?]
  (fn [event]
    (let [e   (aget event "target")
          btn (aget event "button")]
      (when (and (zero? btn) @mouse-down? (not (gclass/has e "ignore")) (not (is-in-checked-hit e)))
        (gclass/enable e "highlighted" @highlighted?)
        (re-frame/dispatch
         [:mark-token
          {:hit-id (get-hit-id e)
           :token-id (gdataset/get e "id")
           :flag @highlighted?}])))))

(defn on-mouse-up [mouse-down? highlighted?]
  (fn [event]
    (let [btn (aget event "button")
          e   (aget event "target")]
      (when (and (zero? btn) (not (gclass/has e "ignore")))
        (swap! mouse-down? not)))))

(defn hit-token [{:keys [id word match marked anns]}]
  (let [filtered-users-colors (re-frame/subscribe [:filtered-users-colors])
        project (re-frame/subscribe [:session :active-project :name])]
    (fn [{:keys [id word match marked anns] :as token}]
      (let [highlighted (if marked "highlighted " "")
            color (when anns (highlight-annotation token @project @filtered-users-colors))
            is-match (when match "info")]
        [:td
         {:class (str highlighted is-match)
          :style {:box-shadow color}
          :data-id id}
         word]))))

(defn on-double-click [hit-idx]
  (fn [event]
    (aset event "cancelBubble" true)
    (re-frame/dispatch [:fetch-snippet hit-idx])))

(defn results-row [hit-num tabindex  {:keys [hit id meta]}]
  (fn [hit-num tabindex {:keys [hit id meta]}]
    [:tr {:data-hit id :style {:opacity (if (:marked meta) 0.6 1)}}
     (concat
      ;; checkbox
      [^{:key (str hit-num "-check")}
       [:td.ignore
        {:style {:width "20px" :background-color "#F9F9F9" :cursor "pointer"
                 :color (if (:marked meta) "#158CBA" "black")}
         :on-click #(let [elem (.-target %)
                          flag (not (gclass/has elem "checked"))]
                      (gclass/toggle elem "checked")
                      (re-frame/dispatch [:mark-hit {:hit-id id :flag flag}]))}
        [:i.zmdi.zmdi-edit.ignore]]
       ;; hit number
       ^{:key (str hit-num "-num")}
       [:td.ignore.snippet-trigger
        {:style {:width "20px" :background-color "#F9F9F9" :cursor "pointer"}
         :on-double-click (on-double-click hit-num)}
        [:label.ignore
         {:style {:font-weight "bold" :cursor "pointer"}}
         (inc hit-num)]]]
      ;; hit
      (for [token hit]
        ^{:key (str hit-num "-" (:id token))} [hit-token token]))]))

(defn results-table []
  (let [results (re-frame/subscribe [:results])
        from (re-frame/subscribe [:session :query-results :from])
        mouse-down? (reagent/atom false)
        highlighted? (reagent/atom false)]
    (fn []
      [bs/table
       {:responsive true
        :striped true
        :id "table-results"
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
           ^{:key hit-num} [results-row hit-num idx hit-map]))]])))
