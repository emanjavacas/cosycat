(ns cleebo.annotation.components.annotation-component
  (:require-macros [cljs.core.async.macros :refer [go-loop]])
  (:require [cljs.core.async :refer [chan <! >! put!]]
            [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [react-bootstrap.components :as bs]
            [goog.dom.dataset :as gdataset]
            [goog.dom.classes :as gclass]
            [cleebo.components :refer [prepend-cell dummy-cell number-cell]]
            [cleebo.annotation.components.annotation-row :refer [annotation-rows]]
            [cleebo.annotation.components.input-row :refer [input-row]]
            [cleebo.utils :refer [->int filter-dummy-tokens]]
            [cleebo.app-utils :refer [disjconj flip]]
            [taoensso.timbre :as timbre]))

(defn on-click
  [token-id hit-id selection shift?]
  (fn [event]
    (let [e (aget event "target")
          my-selection (get @selection hit-id)]
      (.preventDefault event)
      (gclass/toggle e "selected")
      (if @shift?
        (let [max (apply max my-selection)
              min (apply min my-selection)
              [from to] (cond (> token-id max) [(inc max) token-id]
                              (< token-id min) [token-id (dec min)]
                              :else            [token-id max])]
          (swap! selection update-in [hit-id] flip (apply hash-set (range from (inc to)))))
        (if (gclass/has e "selected")
          (swap! selection update-in [hit-id] disj token-id)
          (swap! selection update-in [hit-id] conj token-id))))))

(defn on-key-shift [shift?]
  (fn [event]
    (.preventDefault event)
    (when (= 16 (.-keyCode event))
      (swap! shift? not))))

(defn cell-class [has-anns? is-match? is-selected?]
  (str; (when has-anns? "has-annotation ")
       (when is-match? "info ")
       (when is-selected? "highlighted")))

(defn hit-number-cell [n hit-id open-hits]
  (fn [n hit-id open-hits]
    [:td {:style {:background-color "#f5f5f5"}
          :on-click #(swap! open-hits disjconj hit-id)} n]))

(defn hit-row
  "component for a (currently being annotated) hit row"
  [{hit :hit hit-id :id meta :meta} open-hits & args]
  (let [shift? (reagent/atom false)]
    (fn hit-row
      [{hit :hit hit-id :id meta :meta} open-hits
       & {:keys [row-number selection]}]
      (into
       [:tr
        {:style {:background-color "#cedede" :cursor "pointer"}}]
       (-> (for [{id :id word :word match :match anns :anns} (filter-dummy-tokens hit)
                 :let [id (->int id)]]
             ^{:key (str hit-id "-" id)}
             [:td.unselectable        ;avoid text-selection
              {:tab-index 0
               :on-key-down (on-key-shift shift?)
               :on-key-up (on-key-shift shift?)
               ;; :on-double-click #(swap! open-hits disjconj hit-id)
               :on-click (on-click id hit-id selection shift?)
               :class (cell-class anns match (contains? (get @selection hit-id) id))}
              word])
           (prepend-cell
            {:key (str "number" hit-id)
             :child hit-number-cell
             :opts [row-number hit-id open-hits]}))))))

(defn spacer-row
  "empty row for spacing purposes"
  [& {:keys [space] :or {space 8}}]
  (fn spacer-row [_ _] [:tr {:style {:height (str space "px")}}]))

(defmulti annotation-panel-hit
  (fn [{id :id :as hit-map} open-hits] (contains? @open-hits id)))

(defmethod annotation-panel-hit true
  [{hit-id :id hit :hit hit-meta :meta :as hit-map} open-hits & {:keys [project-name]}]
  (concat
   [{:key (str "hit" hit-id)   :component hit-row}
    {:key (str "input" hit-id) :component input-row}
    {:key (str "input-spacer" hit-id) :component (spacer-row :space 16)}]
   (for [{:keys [key component]} (annotation-rows hit-map project-name)]
     {:key key :component component})
   [{:key (str "ann-spacer" hit-id) :component (spacer-row :space 16)}]))

(defn token-cell
  "help component-fn for a standard token cell"
  [token]
  (fn token-cell [token]
    (let [{:keys [word match anns]} token]
      [:td {:class (str (when anns "has-annotation ") (when match "info"))}
       word])))

(defn queue-row
  "component-fn for a hit row"
  [{hit-id :id hit-map :hit hit-meta :meta} open-hits & {:keys [row-number]}]
  (fn queue-row [{hit-id :id hit-map :hit hit-meta :meta} open-hits & {:keys [row-number]}]
    (into
     [:tr
      {:style {:background-color "#f5f5f5" :cursor "pointer"}
       :class "queue-row"
       :on-click #(swap! open-hits disjconj hit-id)}]
     (-> (for [{:keys [id] :as token} (filter-dummy-tokens hit-map)]
           ^{:key id} [token-cell token])
         (prepend-cell
          {:key (str "dummy" hit-id)
           :child number-cell
           :opts [row-number]})))))

(defmethod annotation-panel-hit false
  [{hit-id :id hit-map :hit hit-meta :meta} open-hits]
  [{:key (str "row-" hit-id) :component queue-row}
   {:key (str "spacer-" hit-id) :component (spacer-row)}])

(defn annotation-component [marked-hits open-hits]
  (let [selection (reagent/atom (zipmap (map :id @marked-hits) (repeatedly hash-set)))
        active-project (re-frame/subscribe [:session :active-project :active-project-name])]
    (fn annotation-component [marked-hits open-hits]
      [bs/table
       {:id "table-annotation"
        :responsive true
        :style {:text-align "center"}}
       [:thead]
       [:tbody
        (doall
         (for [[idx hit-map] (map-indexed vector @marked-hits)
               {:keys [key component]} (annotation-panel-hit hit-map open-hits)]
           ^{:key key} [component hit-map open-hits
                        :project-name @active-project
                        :row-number (inc idx)
                        :selection selection]))]])))
