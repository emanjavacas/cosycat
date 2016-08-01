(ns cleebo.annotation.components.annotation-panel
  (:require [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [react-bootstrap.components :as bs]
            [cleebo.utils :refer [filter-dummy-tokens]]
            [cleebo.app-utils :refer [disjconj]]
            [cleebo.components :refer [prepend-cell]]
            [cleebo.annotation.components.input-row :refer [input-row]]
            [cleebo.annotation.components.annotation-row :refer [annotation-row]]))

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

(defn hit-id-cell [hit-id] [:td {:style {:width "100%" :display "table-cell"}} hit-id])

(defn hit-cell [{:keys [word match]}]
  (fn [{:keys [word match]}]
    [:td.unselectable
     {:class (when match "info")}
     word]))

(defn hit-row
  "component for a (currently being annotated) hit row"
  [{hit :hit hit-id :id meta :meta} open-hits]
  (fn [{hit :hit hit-id :id meta :meta} open-hits]
    (into
     [:tr
      {:style {:background-color "#f5f5f5" :cursor "pointer" :width "100%"}
       :on-click #(swap! open-hits disjconj hit-id)}]
     (-> (for [{id :id :as token} (filter-dummy-tokens hit)]
           ^{:key (str "hit" hit-id id)} [hit-cell token])
         (prepend-cell {:key (str hit-id) :child hit-id-cell :opts [hit-id]})))))

(defn open-annotation-component [hit open-hits]
  (fn [hit open-hits]
    [bs/table
     {:id "table-annotation"
      :responsive true
      :style {:width "99%"}}
     [:thead]
     (into
      [:tbody]
      (concat
       [[hit-row hit open-hits]]
       ;; [bs/collapse {:in (contains? @open-hits (:id hit))}]
       [[input-row hit]]
       (for [ann-key (sort-by (juxt :type :key) > (ann-types hit))]
         [annotation-row hit ann-key])))]))

(defn closed-annotation-component []
  (fn [hit open-hits]
    [bs/table
     {:id "table-annotation"}
     [:thead]
     [:tbody
      [hit-row hit open-hits]]]))

(defn annotation-component [hit open-hits]
  (fn [hit open-hits]
    (if (contains? @open-hits (:id hit))
      [open-annotation-component hit open-hits]
      [closed-annotation-component hit open-hits])))

(defn annotation-panel []
  (let [marked-hits (re-frame/subscribe [:marked-hits {:has-marked? false}])
        open-hits (reagent/atom #{})]
    (fn []
      [:div.container-fluid
       (doall (for [{hit-id :id :as hit} @marked-hits]
                ^{:key (str hit-id)}
                [:div.row [annotation-component hit open-hits]]))])))
