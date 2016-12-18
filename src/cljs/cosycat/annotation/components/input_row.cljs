(ns cosycat.annotation.components.input-row
  (:require [cljs.core.async :refer [<! chan put!]]
            [reagent.core :as reagent]
            [react-bootstrap.components :as bs]
            [re-frame.core :as re-frame]
            [cosycat.utils :refer [parse-annotation nbsp]]
            [cosycat.app-utils :refer [->int parse-token-id]]
            [cosycat.components :refer [prepend-cell dummy-cell]]
            [cosycat.autosuggest :refer [suggest-annotations]]
            [taoensso.timbre :as timbre])
  (:require-macros [cljs.core.async.macros :refer [go-loop]]))

(def border-style
  {:border "1px solid darkgray"})

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

(defn dispatch-span-annotation
  [ann-map hit-id token-ids chans unmerge-on-dispatch? db-path]
  (let [sorted-ids (sort-by #(-> % parse-token-id :id) token-ids)
        from (first sorted-ids)
        to (last sorted-ids)]
    (when unmerge-on-dispatch? (unmerge-cells (first token-ids) chans))
    (re-frame/dispatch [:dispatch-simple-annotation
                        {:ann-data {:ann-map ann-map :hit-id hit-id :token-from from :token-to to}
                         :db-path db-path}])))

(defn on-key-down
  "`unmerge-on-dispatch?` is a bool indicating whether to clear selection after dispatch"
  [{hit-id :id {query :query} :meta :as hit-map} token-ids {:keys [value chans]} unmerge-on-dispatch? db-path]
  (fn [pressed]
    (.stopPropagation pressed)
    (when (= 13 (.-keyCode pressed))
      (if-let [new-ann (parse-annotation (.. pressed -target -value))]
        (let [ann-map {:ann new-ann :query query}]
          (condp = (count token-ids)
            0 (re-frame/dispatch [:notify {:message "Empty selection"}])
            1 (re-frame/dispatch [:dispatch-simple-annotation
                                  {:ann-data {:ann-map ann-map :hit-id hit-id :token-from (first token-ids)}
                                   :db-path db-path}])
            (dispatch-span-annotation ann-map hit-id token-ids chans unmerge-on-dispatch? db-path))
          (reset! value ""))))))

(defn input-component [{hit-id :id :as hit-map} token-id chans unmerge-on-dispatch? db-path]
  (let [tagsets (re-frame/subscribe [:selected-tagsets])
        value (reagent/atom "")]
    (fn [{hit-id :id :as hit-map} token-id chans unmerge-on-dispatch? db-path]
      [:div.input-cell
       [suggest-annotations
        @tagsets
        {:id (str "input-" hit-id)
         :class "form-control input-cell"
         :value value
         :onChange #(reset! value (.. % -target -value))
         :onKeyDown
         (on-key-down hit-map (keys @chans) {:value value :chans chans} unmerge-on-dispatch? db-path)}]])))

(defn hidden-input-cell []
  (fn [] [:td {:style {:display "none"}}]))

(defn visible-input-cell [hit-map token-id chans metadata unmerge-on-dispatch? db-path]
  (fn [hit-map token-id chans metadata unmerge-on-dispatch? db-path]
    [:td {:style (merge {:padding "0px"} border-style)
          :colSpan (count @chans)
          :on-mouse-down #(input-mouse-down metadata (get @chans token-id))
          :on-mouse-enter #(input-mouse-over token-id metadata chans)
          :on-double-click #(unmerge-cells token-id chans)}
     [input-component hit-map token-id chans unmerge-on-dispatch? db-path]]))

(defn input-cell [hit-map token-id metadata & {:keys [unmerge-on-dispatch? db-path]}]
  (let [display (reagent/atom true)
        chans (reagent/atom {token-id (chan)})]
    (handle-chan-events token-id display chans)
    (fn [hit-map token-id metadata & {:keys [unmerge-on-dispatch?]}]
      (if @display
        [visible-input-cell hit-map token-id chans metadata unmerge-on-dispatch? db-path]
        [hidden-input-cell]))))

(defn on-click-pager
  [{:keys [hit-id dir db-path corpus]}]
  (fn []
    (re-frame/dispatch
     [:shift-hit (cond-> {:hit-id hit-id :dir dir :db-path db-path}
                   corpus (assoc :corpus corpus))])))

(defn pager-cell [hit-id db-path corpus]
  (fn [hit-id db-path corpus]
    [:td {:style {:padding "0" :line-height "1.3em"}}
     [:table
      {:display "table-cell" :width "100%"}
      [:tbody
       [:tr
        [:td.hit-pager
         {:style {:font-size "small" :cursor "pointer"}
          :onClick (on-click-pager {:hit-id hit-id :dir :left :db-path db-path :corpus corpus})}
         [bs/glyphicon {:glyph "chevron-left" :style {:vertical-align "text-bottom"}}]]
        [:td.hit-pager
         {:style {:font-size "small" :cursor "pointer"}
          :onClick (on-click-pager {:hit-id hit-id :dir :right :db-path db-path :corpus corpus})}
         [bs/glyphicon {:glyph "chevron-right" :style {:vertical-align "text-bottom"}}]]]]]]))

(defn input-row
  [{hit :hit hit-id :id meta :meta :as hit-map} db-path & {:keys [unmerge-on-dispatch? corpus]}]
  ;; TODO unmerge should depend on project settings
  (let [metadata {:mouse-down (reagent/atom false) :source (reagent/atom nil)}]
    (fn [{hit :hit hit-id :id meta :meta} db-path & {:keys [unmerge-on-dispatch? corpus]}]
      (into [:tr
             {:on-mouse-leave #(reset-metadata! metadata)
              :on-mouse-up #(reset-metadata! metadata)}]
            (-> (for [{token-id :id word :word match :match} hit]
                  ^{:key (str hit-id "-" token-id)}
                  [input-cell hit-map token-id metadata
                   :db-path db-path
                   :unmerge-on-dispatch? unmerge-on-dispatch?])
                (prepend-cell {:key (str hit-id "pager")
                               :child pager-cell
                               :opts [hit-id db-path corpus]}))))))
