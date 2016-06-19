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

(defn get-anns-in-project [anns project-name]
  (-> anns (get project-name) vals))

(defn ann-types
  "extracts the unique annotation keys in a given hit"
  [{hit :hit} project-name]
  (->> (mapcat (fn [{anns :anns :as token}] (get-anns-in-project anns project-name)) hit)
       (map parse-ann-type)
       (map :key)
       (into (hash-set))))

(defn hit-id-cell [hit-id] [:td hit-id])

(defn hit-row
  "component for a (currently being annotated) hit row"
  [{hit :hit hit-id :id meta :meta} open-hits]
  (fn [{hit :hit hit-id :id meta :meta} open-hits]
    (into
     [:tr
      {:style {:background-color "#f5f5f5" :cursor "pointer"}
       :on-click #(swap! open-hits disjconj hit-id)}]
     (-> (for [{id :id word :word match :match anns :anns} (filter-dummy-tokens hit)]
              ^{:key (str "hit" hit-id id)}
           [:td.unselectable {:class (when match "info")} word])
         (prepend-cell {:key (str hit-id) :child hit-id-cell :opts [hit-id]})))))

(defn open-annotation-component [hit project-name open-hits]
  (fn [hit project-name open-hits]
    [bs/table
     {:id "table-annotation"}
     [:thead]
     (into
      [:tbody]
      (concat
       [[hit-row hit open-hits]]
       [[input-row hit]]
       (for [ann-key (sort-by (juxt :type :key) > (ann-types hit project-name))]
         [annotation-row hit ann-key project-name])))]))

(defn closed-annotation-component []
  (fn [hit project-name open-hits]
    [bs/table
     {:id "table-annotation"}
     [:thead]
     [:tbody
      [hit-row hit open-hits]]]))

(defn annotation-component [hit project-name open-hits]
  (fn [hit project-name open-hits]
    (if (contains? @open-hits (:id hit))
      [open-annotation-component hit project-name open-hits]
      [closed-annotation-component hit project-name open-hits])))

(defn annotation-panel []
  (let [marked-hits (re-frame/subscribe [:marked-hits {:has-marked? false}])
        project-name (re-frame/subscribe [:session :active-project :name])
        open-hits (reagent/atom (into (hash-set) (map :id @marked-hits)))]
    (fn []
      [:div.container-fluid
       (doall (for [{hit-id :id :as hit} @marked-hits]
                ^{:key (str hit-id)}
                [:div.row [annotation-component hit @project-name open-hits]]))])))
