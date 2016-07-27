(ns cleebo.annotation.components.annotation-row
  (:require [react-bootstrap.components :as bs]
            [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [cleebo.utils :refer [human-time ->int filter-dummy-tokens ->box]]
            [cleebo.components :refer [user-thumb prepend-cell]]
            [cleebo.annotation.components.annotation-popover :refer [annotation-popover]]
            [taoensso.timbre :as timbre]))

(defn key-val
  [{{k :key v :value} :ann user :username time :timestamp}]
  [:div
   [:span {:style {:text-align "right" :margin-left "7px"}} [bs/label v]]])

(defn annotation-cell-style
  [color-map username]
  (if-let [color (get color-map username)]
    {:box-shadow (->box color)}
    {:opacity "0.25"}))

(defmulti annotation-cell
  "Variadic annotation cell dispatching on span type"
  (fn [{{{span-type :type} :span} :ann-map token-id :token-id color-map :color-map}]
    span-type))

(defmethod annotation-cell "token"
  [{:keys [ann-map color-map token-id]}]
  (fn [{{username :username :as ann-map} :ann-map color-map :color-map}]
    [bs/overlay-trigger
     {:overlay (annotation-popover ann-map)
      :trigger "click"
      :rootClose true
      :placement "bottom"}
     [:td.ann-cell       
      {:style (annotation-cell-style @color-map username)}
      [key-val ann-map]]]))

(defmethod annotation-cell "IOB"
  [{:keys [ann-map color-map token-id]}]
  (fn [{{{{B :B O :O} :scope} :span username :username anns :anns :as ann-map} :ann-map
        color-map :color-map
        token-id :token-id}]
    [bs/overlay-trigger
     {:overlay (annotation-popover ann-map)
      :trigger "click"
      :rootClose true
      :placement "bottom"}
     [:td.ann-cell
      {:style (annotation-cell-style @color-map username)}
      [:span (when (= B (->int token-id)) [key-val ann-map])]]]))

(defmethod annotation-cell :default [_] [:td ""])

(defn annotation-row [hit ann-key]
  (let [color-map (re-frame/subscribe [:filtered-users-colors])]
    (fn [{hit-id :id hit :hit} ann-key]
      (into
       [:tr.ann-row]
       (-> (for [{token-id :id anns :anns} (filter-dummy-tokens hit)]
             ^{:key (str ann-key hit-id token-id)}
             [annotation-cell {:ann-map (get anns ann-key) :token-id token-id :color-map color-map}])
           (prepend-cell {:key (str ann-key) :child (fn [key] [:td key]) :opts [ann-key]}))))))
