(ns cleebo.query.components.results-table
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
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
          {:hit-num (js/parseInt (gdataset/get (gdom/getParentElement e) "hit"))
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
          {:hit-num (js/parseInt (gdataset/get (gdom/getParentElement e) "hit"))
           :token-id (gdataset/get e "id")
           :flag @highlighted?}])))))

(defn on-mouse-up [mouse-down? highlighted?]
  (fn [event]
    (let [button (aget event "button")]
      (when (and (zero? button) (not (gclass/has (aget event "target") "check")))
        (swap! mouse-down? not)))))

(defn hit-token [{:keys [id word match marked ann]}]
  (fn [{:keys [id word match marked ann]}]
    (let [highlighted (if marked "highlighted" "")
          info (if match "info" "")]
      [:td
       {:class (str info highlighted) :data-id id
        :style {:border-bottom (if ann "5px turquoise solid")}}
       word])))

(defn results-row [hit-num hit meta]
  (fn [hit-num hit meta]
    [:tr {:data-hit hit-num}
     (concat
      ;; checkbox
      [^{:key (str hit-num "-check")}
       [:td.check {:style {:width "20px" :background-color "#eeeeee"}}
        [:input.check {:type "checkbox"
                       :checked (or (:marked meta) (:has-marked meta))
                       :on-change #(let [flag (.-checked (.-target %))]
                                     (re-frame/dispatch
                                      [:mark-hit
                                       {:hit-num hit-num
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
          (for [[hit-num {:keys [hit meta]}] (sort-by first @results)]
            ^{:key hit-num} [results-row hit-num hit meta])
          (for [[hit-num {:keys [hit meta]}] (sort-by first @results)]
            ^{:key (str hit-num "rep")} [:tr])))]])))
