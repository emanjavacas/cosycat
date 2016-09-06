(ns cosycat.annotation.components.annotation-row
  (:require [react-bootstrap.components :as bs]
            [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [cosycat.utils :refer [human-time ->int filter-dummy-tokens ->box]]
            [cosycat.components :refer [user-thumb prepend-cell]]
            [cosycat.annotation.components.annotation-popover :refer [annotation-popover]]
            [taoensso.timbre :as timbre]))

(defn key-val
  [{{k :key v :value} :ann user :username time :timestamp}]
  [:div
   [:span {:style {:text-align "right" :margin-left "7px"}}
    [bs/label
     {:bsStyle "primary"
      :style {:font-size "85%"}}
     v]]])

(defn annotation-cell-style
  [color-map username]
  (if-let [color (get color-map username)]
    {:box-shadow (->box color)}
    {:opacity "0.25"}))

(defn annotation-cell [{:keys [ann-map color-map token-id hit-id]}]
  (let [open? (reagent/atom false), target (reagent/atom nil)]
    (fn [{{{{B :B O :O} :scope type :type} :span
           username :username anns :anns :as ann-map} :ann-map
          color-map :color-map hit-id :hit-id token-id :token-id}]
      (if ann-map                       ;when ann is present
        [:td.ann-cell
         {:style (annotation-cell-style @color-map username)
          :on-click #(do (reset! target (.-target %)) (swap! open? not))}
         [:div (case type
                 "IOB" [:span (when (= B (->int token-id)) [key-val ann-map])]
                 "token" [key-val ann-map])
          [bs/overlay
           {:show @open?
            :target (fn [] @target)     ;DOMNode
            :rootClose true
            :onHide #(swap! open? not)  ;called when rootClose triggers
            :placement "right"}
           (annotation-popover
            {:ann-map ann-map
             :hit-id hit-id
             :on-dispatch #(swap! open? not)})]]]
        [:td ""]))))

(defn annotation-key [key]
  [:td [bs/label {:style {:font-size "85%"}} key]])

(defn annotation-row [hit ann-key]
  (let [color-map (re-frame/subscribe [:filtered-users-colors])]
    (fn [{hit-id :id hit :hit} ann-key]
      (into
       [:tr.ann-row {:data-hitid hit-id}]
       (-> (for [{token-id :id anns :anns} (filter-dummy-tokens hit)]
             ^{:key (str ann-key hit-id token-id)}
             [annotation-cell {:ann-map (get anns ann-key)
                               :hit-id hit-id :token-id token-id :color-map color-map}])
           (prepend-cell {:key (str ann-key) :child annotation-key :opts [ann-key]}))))))
