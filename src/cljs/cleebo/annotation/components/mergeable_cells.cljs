(ns cleebo.annotation.components.mergeable-cells
  (:require [cljs.core.async :refer [<! chan put!]]
            [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [cleebo.utils :refer [filter-dummy-tokens]]
            [cleebo.annotation.components.annotation-row :refer [ann-rows annotation-row ann-types]]
            [react-bootstrap.components :as bs])
  (:require-macros [cljs.core.async.macros :refer [go-loop]]))

(def border "1px solid grey")

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

(defn spread-mouse-over [id metadata chans]
  (let [ch (@chans id)
        source-ch @(:source metadata)]
    (when (and @(:mouse-down metadata) (not (= ch source-ch)))
      (put! source-ch [:merge @chans])
      (put! ch [:display false])
      (reset! chans {id ch}))))

(defn unmerge-cells [id chans]
  (doseq [[id ch] @chans] (put! ch [:display true]))
  (reset! chans {id (@chans id)}))

(defn handle-chan-events [id display chans]
  (go-loop []
    (let [[action data] (<! (@chans id))]
      (case action
        :display (reset! display data)
        :merge   (swap! chans merge data))
      (recur))))

(defn input []
  (let [text (reagent/atom "asd")]
    (fn []
      [:input.from-control
       {:style {:width "100%"}
        :type "text"
        :name "input-row"
        :on-change #(.log js/console %)
        :on-key-down #(.log js/console %)}])))

(defn spread-cell [id metadata]
  (let [display (reagent/atom true)
        chans (reagent/atom {id (chan)})]
    (handle-chan-events id display chans)
    (fn [id metadata]
      [:td
       {:style {:display (if @display "table-cell" "none") :border border}
        :colSpan (count @chans)
        :on-mouse-down #(spread-mouse-down metadata (@chans id))
        :on-mouse-enter #(spread-mouse-over id metadata chans)
        :on-double-click #(unmerge-cells id chans)}
       [input]])))

(defn spread-row [{hit :hit hit-id :id meta :meta}]
  (let [metadata {:mouse-down (reagent/atom false) :source (reagent/atom nil)}]
    (fn [{hit :hit hit-id :id meta :meta}]
      [:tr
       {:on-mouse-leave #(reset-metadata! metadata)
        :on-mouse-up #(reset-metadata! metadata)}
       (doall (for [{id :id word :word match :match} (filter-dummy-tokens hit)]
                ^{:key (str hit-id "-" id)}
                [spread-cell id metadata]))])))

(defn ann-comp [{hit-id :id :as hit} project-name]
  (let [ann-keys (sort-by (juxt :type :key) > (ann-types hit project-name))
        rows (concat [{:component hit-row :k (str "hit" hit-id)}]
                     [{:component spread-row :k (str "spread" hit-id)}]
                     (for [{ann-key :key} ann-keys]
                       {:component annotation-row :ann-key ann-key
                        :k (str ann-key hit-id)}))]
    (fn [hit project-name]
      [bs/table
       {:style {:width "100%"}}
       [:thead]
       [:tbody
        (doall (for [{:keys [component k ann-key]} rows]
                 ^{:key k}
                 [component hit ann-key project-name]))]])))

(defn annotation-component [hits]
  (let [project-name (re-frame/subscribe [:session :active-project :name])]
    (fn [hits]
      [:div.container-fluid
       (doall (for [{id :id :as hit} @hits]
                ^{:key (str "hit-" id)}
                [ann-comp hit @project-name]))])))

