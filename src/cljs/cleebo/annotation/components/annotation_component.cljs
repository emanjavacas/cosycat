(ns cleebo.annotation.components.annotation-component
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [react-bootstrap.components :as bs]
            [cleebo.annotation.components.annotation-row :refer [annotation-rows]]
            [cleebo.annotation.components.input-row :refer [input-row]]
            [taoensso.timbre :as timbre]))

(defn token-cell [token & [props]]
  (fn [token]
    (let [{:keys [word match anns]} token]
      [:td
       (merge props {:class (str (when anns "has-annotation ") (when match "info"))})
       word])))

(defn hit-row [{:keys [hit id meta]}]
  (fn [{:keys [hit id meta]}]
    (into
     [:tr
      {:style {:background-color "#cedede"}}]
     (for [token hit]
       ^{:key (str id "-" (:id token))} [token-cell token]))))

(defn queue-row
  "component for a queue row"
  [current-hit-id]
  (fn [{:keys [hit id]}]
    (into
     [:tr
      {:style {:background-color "#f5f5f5" :cursor "pointer"}
       :class "queue-row"
       :on-click #(reset! current-hit-id id)}]
     (for [{:keys [id] :as token} hit]
       ^{:key id} [token-cell token]))))

(defn spacer [& [space]]
  (fn []
    [:tr {:style {:height (str (or space 8) "px")}}]))

(defn get-target-hit-id
  [marked-hits current-ann-hit-id]
  (if-not current-ann-hit-id
    (:id (first marked-hits))
    current-ann-hit-id))

(defn component-rows
  "transforms hits into a vector of vectors [id component-fn];
  one hit may turn into multiple components (rows)"
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
        ["input-spacer" (spacer)]]
       (annotation-rows hit-map)
       [["ann-spacer" (spacer 16)]]))))

(defn annotation-component [marked-hits]
  (let [current-hit-id (reagent/atom nil)]
    (fn [marked-hits]
      [bs/table
       {:responsive true
        :id "table-annotation"
        :style {:text-align "center"}}
       [:thead]
       [:tbody
        (doall
         (for [hit-map @marked-hits
               [id row-fn] (component-rows hit-map marked-hits current-hit-id)]
           ^{:key id} [row-fn hit-map]))]])))
