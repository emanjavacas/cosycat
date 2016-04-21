(ns cleebo.annotation.components.annotation-component
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [react-bootstrap.components :as bs]
            [goog.dom.dataset :as gdataset]
            [goog.dom.classes :as gclass]
            [cleebo.annotation.components.annotation-row :refer [annotation-rows]]
            [cleebo.annotation.components.input-row :refer [input-row]]
            [cleebo.utils :refer [->int filter-dummy-tokens]]
            [taoensso.timbre :as timbre]))

(defn on-mouse-down [mouse-down? highlighted? selection id]
  (fn [event]
    (let [e (aget event "target")]
      (.preventDefault event)
      (gclass/toggle e "highlighted")
      (swap! mouse-down? not)
      (reset! highlighted? (gclass/has e "highlighted"))
      (if @highlighted?
        (swap! selection conj id)
        (swap! selection disj id)))))

(defn on-mouse-over [mouse-down? highlighted? selection id]
  (fn [event]
    (let [e (aget event "target")]
      (.preventDefault event)
      (when @mouse-down?
        (gclass/enable e "highlighted" @highlighted?)
        (if @highlighted?
          (swap! selection conj id)
          (swap! selection disj id))))))

(defn on-mouse-up [mouse-down?]
  (fn [event]
    (.preventDefault event)
    (swap! mouse-down? not)))

(defn flip
  "removes the interesection of S1S2 from the union S1S2"
  [set1 set2]
  (-> set1
      (clojure.set/union set2)
      (clojure.set/difference (clojure.set/intersection set1 set2))))

(defn on-click
  [token-id selection shift?]
  (fn [event]
    (let [e (aget event "target")]
      (.preventDefault event)
      (gclass/toggle e "selected")
      (if @shift?
        (let [max (apply max @selection)
              min (apply min @selection)
              [from to] (cond (> token-id max) [(inc max) token-id]
                              (< token-id min) [token-id (dec min)]
                              :else            [token-id max])]
          (swap! selection flip (apply hash-set (range from (inc to)))))
        (if (gclass/has e "selected")
          (swap! selection disj token-id)
          (swap! selection conj token-id))))))

(defn on-key-shift [shift?]
  (fn [event]
    (.preventDefault event)
    (when (= 16 (.-keyCode event))
      (swap! shift? not))))

(defn token-cell
  "help component-fn for a standard token cell"
  [token & [props]]
  (fn [token & [props]]
    (let [{:keys [word match anns]} token]
      [:td
       (merge props {:class (str (when anns "has-annotation ") (when match "info"))})
       word])))

(defn hit-row
  "component for a (currently being annotated) hit row"
  [{hit :hit id :id meta :meta} & {:keys [span-selection]}]
  (let [mouse-down? (reagent/atom false)
        highlighted? (reagent/atom false)
        shift? (reagent/atom false)]
    (reagent/create-class
     {:component-will-receive-props
      #(do (reset! mouse-down? false)
           (reset! highlighted? false)
           (reset! span-selection #{}))    
      :reagent-render
      (fn [{hit :hit id :id meta :meta} & {:keys [span-selection]}]
        (into
         [:tr
          {:style {:background-color "#cedede"}}]
         (for [{token-id :id word :word match :match anns :anns} (filter-dummy-tokens hit)
               :let [token-id (->int token-id)]]
           ^{:key (str id "-" token-id)}
           [:td.unselectable            ;avoid text-selection
            {:tab-index 0               ;enable key- events
             :on-key-down (on-key-shift shift?)
             :on-key-up (on-key-shift shift?)
             :on-click (on-click token-id span-selection shift?)             
             :class (str (when anns "has-annotation ") (when match "info ")
                         (when (contains? @span-selection (->int token-id)) "highlighted"))}
            word])))})))

(defn queue-row-fn
  "component-fn for a queue row"
  [current-hit-id]
  (fn [{hit :hit id :id}]
    (into
     [:tr
      {:style {:background-color "#f5f5f5" :cursor "pointer"}
       :class "queue-row"
       :on-click #(reset! current-hit-id id)}]
     (for [{:keys [id] :as token} (filter-dummy-tokens hit)]
       ^{:key id} [token-cell token]))))

(defn spacer
  "empty row for spacing purposes"
  [& [space]]
  (fn []
    [:tr {:style {:height (str (or space 8) "px")}}]))

(defn get-target-hit-id
  "returns the id of the hit that is currently selected for annotation"
  [marked-hits current-ann-hit-id]
  (if-not current-ann-hit-id
    (:id (first marked-hits))
    current-ann-hit-id))

(defn table-row-components
  "Transforms hits into a vector of vectors [id component-fn].
  Each component-fn is passed a hit-map as argument and may
  return one or multiple rows"
  [{:keys [id] :as hit-map} & {:keys [marked-hits current-hit-id]}]
  (let [target-hit-id (get-target-hit-id @marked-hits @current-hit-id)]
    (if-not (= id target-hit-id)
      ;; queue-row
      [[(str "row-" id) (queue-row-fn current-hit-id)]
       [(str "spacer-" id) (spacer)]]
      ;; annotation-row
      (concat
       [["hit"   hit-row]
        ["input" input-row]
        ["input-spacer" (spacer 16)]]
       (annotation-rows hit-map)
       [["ann-spacer" (spacer 16)]]))))

(defn annotation-component [marked-hits]
  (let [current-hit-id (reagent/atom nil)
        span-selection (reagent/atom #{})]
    (fn [marked-hits]
      [bs/table
       {;:responsive true
        :id "table-annotation"
        :style {:text-align "center"}}
       [:thead]
       [:tbody
        (doall
         (for [hit-map @marked-hits
               [id row-fn] (table-row-components hit-map
                                                 :marked-hits marked-hits
                                                 :current-hit-id current-hit-id)]
           ^{:key id} [row-fn hit-map :span-selection span-selection]))]])))

