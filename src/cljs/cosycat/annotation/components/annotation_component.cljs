(ns cosycat.annotation.components.annotation-component
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

(defn on-double-click [hit-id show-hit-id? db-path]
  (fn [event]
    (.stopPropagation event)
    (when show-hit-id?
      (do (re-frame/dispatch [:open-modal [db-path :snippet]])
          (re-frame/dispatch [:fetch-snippet hit-id])))))

(defn hit-id-cell [hit-id {num :num} {:keys [show-hit-id? db-path]}]
  [:td.unselectable
   {:style {:width "100%" :display "table-cell" :font-weight "bold"}
    :on-double-click (on-double-click hit-id show-hit-id? db-path)}
   (when show-hit-id? (if num (inc num) hit-id))])

(defn hit-cell [{:keys [word match]} hit-id color-map & {:keys [editable? show-match?]}]
  (fn [{:keys [word match anns] :as token-map} hit-id color-map & {:keys [editable? show-match?]}]
    (let [color (when anns (highlight-annotation token-map @color-map))]
      [:td.unselectable
       {:class (when (and show-match? match) "info")
        :on-click #(when editable? (re-frame/dispatch [:open-hit hit-id]))
        :style {:box-shadow color}}
       word])))

(defn hit-row
  "component for a (currently being annotated) hit row"
  [{hit :hit hit-id :id meta :meta} color-map
   & {:keys [editable? show-match? show-hit-id? db-path] :as opts}]
  (fn [{hit :hit hit-id :id meta :meta} color-map
       & {:keys [editable? show-match? show-hit-id? db-path] :as opts}]
    (into
     [:tr
      {:style {:background-color "#eeeeee" :cursor (when editable? "pointer") :width "100%"}}]
     (-> (for [{id :id :as token} hit]
           ^{:key (str "hit" hit-id id)}
           [hit-cell token hit-id color-map :editable? editable? :show-match? show-match?])
         (prepend-cell {:key (str hit-id)
                        :child hit-id-cell
                        :opts [hit-id meta opts]})))))

(defn annotation-component
  [{hit-id :id :as hit-map} & opts]
  (fn [{hit-id :id :as hit-map} color-map
       & {:keys [corpus ;; in case it can't be inferred from query settings
                 db-path ;; path from project to hit-map (defaults to query path)
                 editable? ;; whether to allow annotation dispatches or not
                 show-hit-id? ;; whether to display hit id (and snippet viz)
                 show-match? ;; whether to highlight match tokens
                 unmerge-on-dispatch? ;; unmerge input cells after dispatch
                 highlight-fn] ;; a pred of ann-map to decide whether to highlight
          :or {db-path :query
               editable? true
               show-hit-id? true
               show-match? true
               unmerge-on-dispatch? false
               highlight-fn (constantly false)}}]
    [bs/table
     {:id "table-annotation"
      :style {:border-collapse "collapse" :border "1px" :border-style "inset"}
      :responsive true}
     [:thead]
     (into
      [:tbody]
      (concat
       [[hit-row hit-map color-map
         :db-path db-path
         :editable? editable?
         :show-hit-id? show-hit-id?
         :show-match? show-match?]]
       (when editable?
         [[input-row hit-map
           :db-path db-path
           :corpus corpus
           :unmerge-on-dispatch? unmerge-on-dispatch?]])
       (for [ann-key (sort-by :key > (ann-types hit-map))]
         [annotation-row hit-map ann-key color-map
          :db-path db-path
          :editable? editable?
          :highlight-fn highlight-fn])))]))
