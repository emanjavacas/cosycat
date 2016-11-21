(ns cosycat.annotation.components.annotation-panel
  (:require [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [react-bootstrap.components :as bs]
            [cosycat.components :refer [prepend-cell]]
            [cosycat.utils :refer [highlight-annotation]]
            [cosycat.annotation.components.input-row :refer [input-row]]
            [cosycat.annotation.components.annotation-row :refer [annotation-row]]))

(defn parse-ann-type
  [{{key :key} :ann {type :type} :span}]
  {:key key :type type})

(defn ann-types
  "extracts the unique annotation keys in a given hit"
  [{hit :hit}]
  (->> (mapcat (comp vals :anns) hit)
       (map parse-ann-type)
       (map :key)
       (into (hash-set))))

(defn on-double-click [hit-id editable?]
  (fn [event]
    (.stopPropagation event)
    (when editable? (re-frame/dispatch [:fetch-snippet hit-id]))))

(defn hit-id-cell [hit-id {num :num} editable?]
  [:td.unselectable
   {:style {:width "100%" :display "table-cell" :font-weight "bold"}
    :on-double-click (on-double-click hit-id editable?)}
   (if num (inc num) hit-id)])

(defn hit-cell [{:keys [word match]} hit-id color-map & {:keys [editable?] :or {editable? true}}]
  (fn [{:keys [word match anns] :as token-map} hit-id color-map
       & {:keys [editable?] :or {editable? true}}]
    (let [color (when anns (highlight-annotation token-map @color-map))]
      [:td.unselectable
       {:class (when match "info")
        :on-click #(when editable? (re-frame/dispatch [:open-hit hit-id]))
        :style {:box-shadow color}}
       word])))

(defn hit-row
  "component for a (currently being annotated) hit row"
  [{hit :hit hit-id :id meta :meta} color-map & {:keys [editable?] :or {editable? true}}]
  (fn [{hit :hit hit-id :id meta :meta} color-map
       & {:keys [editable?] :or {editable? true}}]
    (into
     [:tr
      {:style {:background-color "#f5f5f5" :cursor (when editable? "pointer") :width "100%"}}]
     (-> (for [{id :id :as token} hit]
           ^{:key (str "hit" hit-id id)} [hit-cell token hit-id color-map :editable? editable?])
         (prepend-cell {:key (str hit-id) :child hit-id-cell :opts [hit-id meta editable?]})))))

(defn annotation-component
  [hit color-map & {:keys [editable?] :or {editable? true}}]
  (fn [hit color-map & {:keys [editable?] :or {editable? true}}]
    [bs/table
     {:id "table-annotation"
      :style {:border-collapse "collapse" :border "1px" :border-style "inset"}
      :responsive true}
     [:thead]
     (into
      [:tbody]
      (concat
       [[hit-row hit color-map :editable? editable?]]
       (when editable? [[input-row hit]])
       (for [ann-key (sort-by (juxt :type :key) > (ann-types hit))]
         [annotation-row hit ann-key :editable? editable?])))]))

(defn closed-annotation-component [hit color-map]
  (fn [hit color-map]
    [bs/table
     {:id "table-annotation"}
     [:thead]
     [:tbody
      [hit-row hit color-map]]]))

(defn annotation-panel []
  (let [marked-hits (re-frame/subscribe [:marked-hits {:has-marked? false}])
        color-map (re-frame/subscribe [:filtered-users-colors])
        open-hits (re-frame/subscribe [:project-session :components :open-hits])]
    (fn []
      [:div.container-fluid
       (doall (for [{hit-id :id :as hit} (sort-by #(get-in % [:meta :num]) @marked-hits)]
                ^{:key (str hit-id)}
                [:div.row
                 (if (contains? @open-hits hit-id)
                   [annotation-component hit color-map]
                   [closed-annotation-component hit color-map])]))])))
