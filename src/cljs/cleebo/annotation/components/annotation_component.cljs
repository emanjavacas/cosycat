(ns cleebo.annotation.components.annotation-component
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [react-bootstrap.components :as bs]
            [goog.dom.dataset :as gdataset]
            [goog.dom.classes :as gclass]
            [cleebo.annotation.components.annotation-row :refer [annotation-rows]]
            [cleebo.annotation.components.input-row :refer [input-row]]
            [cleebo.utils :refer [->int]]
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

(defn token-cell
  "help component-fn for a standard token cell"
  [token & [props]]
  (fn [token]
    (let [{:keys [word match anns]} token]
      [:td
       (merge props {:class (str (when anns "has-annotation ") (when match "info"))})
       word])))

(defn hit-row
  "component-fn for a (currently being annotated) hit row"
  [& {{hit :hit id :id meta :meta} :hit-map span-selection :span-selection}]
  (let [mouse-down? (reagent/atom false)
        highlighted? (reagent/atom false)]
    (reagent/create-class
     {:component-will-receive-props
      #(do (reset! mouse-down? false)
           (reset! highlighted? false)
           (reset! span-selection #{}))    
      :reagent-render
      (fn [& {{hit :hit id :id meta :meta} :hit-map span-selection :span-selection}]
        (into
         [:tr
          {:style {:background-color "#cedede"}}]
         (for [{token-id :id :as token} hit
               :let [token-id (->int token-id)]]
           ^{:key (str id "-" token-id)}
           [token-cell token
            {:on-mouse-down (on-mouse-down mouse-down? highlighted? span-selection token-id)
             :on-mouse-over (on-mouse-over mouse-down? highlighted? span-selection token-id)
             :on-mouse-up (on-mouse-up mouse-down?)
             :style
             {:background-color
              (when (contains? @span-selection (->int token-id)) "white")}}])))})))

(defn queue-row
  "component-fn for a queue row"
  [current-hit-id]
  (fn [& {{hit :hit id :id} :hit-map}]
    (into
     [:tr
      {:style {:background-color "#f5f5f5" :cursor "pointer"}
       :class "queue-row"
       :on-click #(reset! current-hit-id id)}]
     (for [{:keys [id] :as token} hit]
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
  [{:keys [id] :as hit-map} marked-hits current-hit-id]
  (let [target-hit-id (get-target-hit-id @marked-hits @current-hit-id)]
    (if-not (= id target-hit-id)
      ;; queue-row
      [[(str "row-" id) (queue-row current-hit-id)]
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
         (for [hit-map (sort-by :id  @marked-hits)
               [id row-fn] (table-row-components hit-map marked-hits current-hit-id)]
           ^{:key id} [row-fn :hit-map hit-map :span-selection span-selection]))]])))
