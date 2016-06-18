(ns cleebo.annotation.components.mergeable-cells
  (:require [cljs.core.async :refer [<! chan put!]]
            [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [cleebo.utils :refer [filter-dummy-tokens]]
            [react-bootstrap.components :as bs])
  (:require-macros [cljs.core.async.macros :refer [go-loop]]))

(def border "1px solid black")

(defn hit-row [{hit :hit hit-id :id meta :meta}]
  (fn [{hit :hit hit-id :id meta :meta}]
    [:tr
     (doall (for [{id :id word :word match :match} (filter-dummy-tokens hit)]
              ^{:key (str hit-id "-" id)}
              [:td word]))]))

(defn assoc-metadata! [metadata & bindings]
  (doseq [[key val] (partition 2 bindings)]
    (reset! (metadata key) val)))

(defn reset-metadata! [metadata]
  (assoc-metadata! metadata :mouse-down false :source nil))

(defn spread-mouse-down [metadata ch]
  (assoc-metadata! metadata :mouse-down true :source ch))

(defn spread-mouse-over [metadata ch colspan id]
  (when (and @(:mouse-down metadata) (not (= ch @(:source metadata))))
    (put! @(:source metadata) [:merge {:colspan @colspan :ch ch :id id}])
    (put! ch [:display false])))

(defn unmerge-cells [colspan target-chans]
  (doseq [[id ch] @target-chans] (put! ch [:display true]))
  (reset! colspan 1)
  (reset! target-chans {}))

(defn handle-display-event [display flag colspan target-chans]
  (reset! display flag))

(defn handle-merge-event
  [{target-colspan :colspan ch :ch id :id} colspan target-chans]
  (swap! colspan + target-colspan)
  (swap! target-chans assoc id ch))

(defn handle-chan-events [ch display colspan target-chans]
  (go-loop []
    (let [[action data] (<! ch)]
      (case action
        :display (handle-display-event display data colspan target-chans)
        :merge   (handle-merge-event data colspan target-chans))
      (recur))))

(defn spread-cell [id metadata]
  (let [display (reagent/atom true)
        target-chans (reagent/atom {})
        colspan (reagent/atom 1)
        ch (chan)]
    (handle-chan-events ch display colspan target-chans)
    (fn [id metadata]
      [:td
       {:style {:display (if @display "table-cell" "none") :border border}
        :colSpan @colspan
        :on-mouse-down #(do (.preventDefault %) (spread-mouse-down metadata ch))
        :on-mouse-enter #(spread-mouse-over metadata ch colspan id)
        :on-double-click #(unmerge-cells colspan target-chans)}
       (apply str id (keys @target-chans))])))

(defn spread-row [{hit :hit hit-id :id meta :meta}]
  (let [metadata {:mouse-down (reagent/atom false) :source (reagent/atom nil)}]
    (fn [{hit :hit hit-id :id meta :meta}]
      [:tr
       {:on-mouse-leave #(reset-metadata! metadata)
        :on-mouse-up #(reset-metadata! metadata)}
       (doall (for [{id :id word :word match :match} (filter-dummy-tokens hit)]
                ^{:key (str hit-id "-" id)}
                [spread-cell id metadata]))])))

(defn ann-comp [marked-hit]
  (fn [marked-hit]
    [bs/table
     {:style {:width "100%"}}
     [:thead]
     [:tbody
      [hit-row marked-hit]
      [spread-row marked-hit]]]))

(defn annotation-component [marked-hits]
  (fn [marked-hits]
    [:div.container-fluid
     (doall (for [{id :id :as hit} @marked-hits]
              ^{:key (str "hit-" id)} [ann-comp hit]))]))

