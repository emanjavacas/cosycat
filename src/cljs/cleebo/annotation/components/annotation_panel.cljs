(ns cleebo.annotation.components.annotation-panel
  (:require [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [react-bootstrap.components :as bs]
            [cleebo.utils :refer [filter-dummy-tokens]]
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

(defn hit-row
  "component for a (currently being annotated) hit row"
  [{hit :hit hit-id :id meta :meta}]
  (fn [{hit :hit hit-id :id meta :meta}]
    (into
     [:tr {:style {:background-color "#eeeeee"}}]
     (doall (for [{id :id word :word match :match anns :anns} (filter-dummy-tokens hit)]
              ^{:key (str "hit" hit-id id)}
              [:td.unselectable {:class (when match "info")} word])))))

(defn queue-row
  "component-fn for a non active hit row"
  [{hit-id :id hit :hit hit-meta :meta}]
  (fn [{hit-id :id hit :hit hit-meta :meta}]
    (into
     [:tr {:style {:background-color "#f5f5f5" :cursor "pointer"} :class "queue-row"}]
     (doall (for [{:keys [id match word] :as token} (filter-dummy-tokens hit)]
              ^{:key (str "queue" hit-id id)}
              [:td {:class (when match "info")} word])))))

(defn annotation-component [hit project-name]
  (fn [hit project-name]
    [bs/table
     {:id "table-annotation"}
     [:thead]
     (into
      [:tbody]
      (concat
       [[hit-row hit]]
       [[input-row hit]]
       (for [ann-key (sort-by (juxt :type :key) > (ann-types hit project-name))]
         (do (.log js/console (ann-types hit project-name))
             [annotation-row hit ann-key project-name]))))]))

(defn annotation-panel []
  (let [marked-hits (re-frame/subscribe [:marked-hits {:has-marked? false}])
        project-name (re-frame/subscribe [:session :active-project :name])]
    (fn []
      [:div.container-fluid
       (doall (for [{hit-id :id :as hit} @marked-hits]
                ^{:key (str hit-id)}
                [:div.row [annotation-component hit @project-name]]))])))
