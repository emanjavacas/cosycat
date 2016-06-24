(ns cleebo.annotation.components.input-row
  (:require [cljs.core.async :refer [<! chan put!]]
            [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [cleebo.utils :refer [parse-annotation ->int filter-dummy-tokens nbsp]]
            [cleebo.components :refer [prepend-cell dummy-cell]]
            [cleebo.autocomplete :refer [autocomplete-jq]])
  (:require-macros [cljs.core.async.macros :refer [go-loop]]))

(def border "1px solid darkgray")

(defn assoc-metadata! [metadata & bindings]
  (doseq [[key val] (partition 2 bindings)]
    (reset! (metadata key) val)))

(defn reset-metadata! [metadata]
  (assoc-metadata! metadata :mouse-down false :source nil))

(defn input-mouse-down [metadata ch]
  (assoc-metadata! metadata :mouse-down true :source ch))

(defn input-mouse-over [token-id metadata chans]
  (let [ch (@chans token-id)
        source-ch @(:source metadata)]
    (when (and @(:mouse-down metadata) (not (= ch source-ch)))
      (put! source-ch [:merge @chans])
      (put! ch [:display false])
      (reset! chans {token-id ch}))))

(defn unmerge-cells [token-id chans]
  (doseq [[token-id ch] @chans] (put! ch [:display true]))
  (reset! chans {token-id (@chans token-id)}))

(defn handle-chan-events [token-id display chans]
  (go-loop []
    (let [[action data] (<! (@chans token-id))]
      (case action
        :display (reset! display data)
        :merge   (swap! chans merge data))
      (recur))))

(defn handle-span-dispatch [ann hit-id token-ids]
  (let [from (apply min token-ids)
        to (apply max token-ids)]
    (re-frame/dispatch [:dispatch-annotation ann hit-id from to])))

(defn on-key-down
  [hit-id token-ids]
  (fn [pressed]
    (if (= 13 (.-keyCode pressed))
      (if-let [[key val] (parse-annotation (.. pressed -target -value))]
        (let [ann {:key key :value val}]
          (condp = (count token-ids)
            0 (re-frame/dispatch [:notify {:message "Empty selection"}])
            1 (re-frame/dispatch [:dispatch-annotation ann hit-id (first token-ids)])
            (handle-span-dispatch ann hit-id token-ids))
          (set! (.. pressed -target -value) ""))))))

(defn input [hit-id token-id chans]
  (let [text (reagent/atom "")]
    (fn [hit-id token-id chans]
      [:input.from-control.input-cell
       {:type "text"
        :name "input-row"
        :on-key-down (on-key-down hit-id (map ->int (keys @chans)))}])))

(defn hidden-input-cell []
  (fn [] [:td {:style {:display "none"}}]))

(defn visible-input-cell [hit-id token-id chans metadata]
  (fn [hit-id token-id chans metadata]
    [:td {:style {:padding "0px"
                  :border border}
          :colSpan (count @chans)
          :on-mouse-down #(input-mouse-down metadata (get @chans token-id))
          :on-mouse-enter #(input-mouse-over token-id metadata chans)
          :on-double-click #(unmerge-cells token-id chans)}
     [input hit-id token-id chans]]))

(defn input-cell [hit-id token-id metadata]
  (let [display (reagent/atom true)
        chans (reagent/atom {token-id (chan)})]
    (handle-chan-events token-id display chans)
    (fn [hit-id token-id metadata]
      (if @display
        [visible-input-cell hit-id token-id chans metadata]
        [hidden-input-cell]))))

(defn input-row [{hit :hit hit-id :id meta :meta}]
  (let [metadata {:mouse-down (reagent/atom false) :source (reagent/atom nil)}]
    (fn [{hit :hit hit-id :id meta :meta}]
      (into [:tr
             {:style {:width "100%"}
              :on-mouse-leave #(reset-metadata! metadata)
              :on-mouse-up #(reset-metadata! metadata)}]
            (-> (for [{token-id :id word :word match :match} (filter-dummy-tokens hit)]
                  ^{:key (str hit-id "-" token-id)}
                  [input-cell hit-id token-id metadata])
                (prepend-cell {:key (str hit-id "first") :child dummy-cell}))))))
