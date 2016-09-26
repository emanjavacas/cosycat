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

(defn on-double-click [hit-id]
  (fn [event]
    (.stopPropagation event)
    (re-frame/dispatch [:fetch-snippet hit-id])))

(defn hit-id-cell [hit-id {num :num}]
  [:td.unselectable
   {:style {:width "100%" :display "table-cell" :font-weight "bold"}
    :on-double-click (on-double-click hit-id)}
   (if num (inc num) hit-id)])

(defn hit-cell [{:keys [word match]} hit-id color-map]
  (fn [{:keys [word match anns] :as token-map} hit-id color-map]
    (let [color (when anns (highlight-annotation token-map @color-map))]
      [:td.unselectable
       {:class (when match "info")
        :on-click #(re-frame/dispatch [:open-hit hit-id])
        :style {:box-shadow color}}
       word])))

(defn hit-row
  "component for a (currently being annotated) hit row"
  [{hit :hit hit-id :id meta :meta} open-hits color-map]
  (fn [{hit :hit hit-id :id meta :meta} open-hits color-map]
    (into
     [:tr
      {:style {:background-color "#f5f5f5" :cursor "pointer" :width "100%"}}]
     (-> (for [{id :id :as token} hit]
           ^{:key (str "hit" hit-id id)} [hit-cell token hit-id color-map])
         (prepend-cell {:key (str hit-id) :child hit-id-cell :opts [hit-id meta]})))))

(defn open-annotation-component [hit open-hits color-map]
  (fn [hit open-hits color-map]
    [bs/table
     {:id "table-annotation"
      :style {:border-collapse "collapse"
              :border "1px"
              :border-style "inset"}
      :responsive true}
     [:thead]
     (into
      [:tbody]
      (concat
       [[hit-row hit open-hits color-map]]
       [[input-row hit]]
       (for [ann-key (sort-by (juxt :type :key) > (ann-types hit))]
         [annotation-row hit ann-key])))]))

(defn closed-annotation-component [hit open-hits color-map]
  (fn [hit open-hits color-map]
    [bs/table
     {:id "table-annotation"}
     [:thead]
     [:tbody
      [hit-row hit open-hits color-map]]]))

(defn annotation-component [hit open-hits]
  (let [color-map (re-frame/subscribe [:filtered-users-colors])]
    (fn [hit open-hits]
      (if (contains? @open-hits (:id hit))
        [open-annotation-component hit open-hits color-map]
        [closed-annotation-component hit open-hits color-map]))))

(defn annotation-panel []
  (let [marked-hits (re-frame/subscribe [:marked-hits {:has-marked? false}])
        open-hits (re-frame/subscribe [:project-session :components :open-hits])]
    (fn []
      [:div.container-fluid
       (doall (for [{hit-id :id :as hit} (sort-by #(get-in % [:meta :num]) @marked-hits)]
                ^{:key (str hit-id)}
                [:div.row [annotation-component hit open-hits]]))])))
