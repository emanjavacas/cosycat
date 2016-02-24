(ns cleebo.query.components.results-table
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [taoensso.timbre :as timbre]
            [goog.dom :as gdom]
            [goog.dom.classes :as gclass]
            [goog.dom.dataset :as gdataset]
            [react-bootstrap.components :as bs]))

(defn on-mouse-down [mouse-down? highlighted?]
  (fn [event]
    (let [e      (aget event "target")
          button (aget event "button")]
      (.log js/console button)
      (.preventDefault event)                                 ;avoid text selection
      (when (and (zero? button) (not (gclass/has e "check"))) ;check button type
        (gclass/toggle e "highlighted")
        (swap! mouse-down? not)
        (reset! highlighted? (gclass/has e "highlighted"))
        (re-frame/dispatch
         [:mark-token
          {:hit-id (js/parseInt (gdataset/get (gdom/getParentElement e) "hit"))
           :token-id (gdataset/get e "id")
           :flag @highlighted?}]))
      (when (= 2 button)
        (.preventDefault event)))))

(defn on-mouse-over [mouse-down? highlighted?]
  (fn [event]
    (let [e (aget event "target")
          button (aget event "button")]
      (when (and (zero? button) @mouse-down? (not (gclass/has e "check")))
        (gclass/enable e "highlighted" @highlighted?)
        (re-frame/dispatch
         [:mark-token
          {:hit-id (js/parseInt (gdataset/get (gdom/getParentElement e) "hit"))
           :token-id (gdataset/get e "id")
           :flag @highlighted?}])))))

(defn on-mouse-up [mouse-down? highlighted?]
  (fn [event]
    (let [button (aget event "button")]
      (when (and (zero? button) (not (gclass/has (aget event "target") "check")))
        (swap! mouse-down? not)))))

(defn hit-token [{:keys [id word match marked ann]}]
  (fn [{:keys [id word match marked anns]}]
    (let [highlighted (if marked "highlighted" "")
          info (if match "info" "")]
      [:td
       {:class (str info highlighted) :data-id id
        :style {:border-bottom (if anns "5px turquoise solid")}}
       word])))

(defn results-row [hit-num {:keys [hit id meta]}]
  (fn [hit-num {:keys [hit id meta]}]
    [:tr {:data-hit id}
     (concat
      ;; checkbox
      [^{:key (str hit-num "-check")}
       [:td.check {:style {:width "20px" :background-color "#eeeeee"}}
        [:input.check {:type "checkbox"
                       :checked (or (:marked meta) (:has-marked meta))
                       :on-change #(let [flag (.-checked (.-target %))]
                                     (re-frame/dispatch
                                      [:mark-hit
                                       {:hit-id id
                                        :flag flag}]))}]]
       ;; hit number
       ^{:key (str hit-num "-num")}
       [:td.check  {:style {:width "20px" :background-color "#eeeeee"}}
        [:label.check (inc hit-num)]]]
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
       {:responsive true :className "table-results" :id "table"
        :on-mouse-down (on-mouse-down mouse-down? highlighted?)        
        :on-mouse-over (on-mouse-over mouse-down? highlighted?)
        :on-mouse-up (on-mouse-up mouse-down? highlighted?)
        :tab-index 0
        :style {:border-collapse "collapse"}}
       [:thead]
       [:tbody {:style {:font-size "11px"}}
        (doall
         (interleave
          (for [[idx {:keys [hit meta id] :as hit-map}] (map-indexed vector @results)
                :let [hit-num (+ idx @from)]]
            ^{:key hit-num} [results-row hit-num hit-map])
          (for [[idx {:keys [hit meta]}] (map-indexed vector @results)
                :let [hit-num (+ idx @from)]]
            ^{:key (str hit-num "rep")} [:tr])))]])))

