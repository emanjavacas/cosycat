(ns cosycat.annotation.components.annotation-row
  (:require [react-bootstrap.components :as bs]
            [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [cosycat.utils :refer [human-time ->box]]
            [cosycat.app-utils :refer [parse-token-id]]
            [cosycat.components :refer [user-thumb prepend-cell dummy-cell]]
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

(defn annotation-cell [{:keys [ann-map color-map token-id hit-id]} colspan]
  (let [open? (reagent/atom false), target (reagent/atom nil)]
    (fn [{{username :username anns :anns :as ann-map} :ann-map
          color-map :color-map hit-id :hit-id token-id :token-id} colspan]
      (if-not ann-map
        dummy-cell
        [:td.ann-cell
         {:style (annotation-cell-style @color-map username)
          :colSpan colspan
          :on-click #(do (reset! target (.-target %)) (swap! open? not))}
         [:div [key-val ann-map]
          [bs/overlay
           {:show @open?
            :target (fn [] @target)     ;DOMNode
            :rootClose true
            :onHide #(swap! open? not) ;called when rootClose triggers
            :placement "top"}
           (annotation-popover
            {:ann-map ann-map
             :hit-id hit-id
             :on-dispatch #(swap! open? not)})]]]))))

(defn annotation-key [key]
  [:td [bs/label {:style {:font-size "90%"}} key]])

(defn is-B-IOB? [{{{B :B O :O} :scope type :type} :span} token-id]
  (and (= type "IOB") (= B (-> (parse-token-id token-id) :id))))

(defn with-colspans [hit ann-key]
  (reduce (fn [acc {token-id :id anns :anns :as token}]
            (let [{{{B :B O :O} :scope type :type} :span :as ann} (get anns ann-key)]
              (cond (not type) (conj acc {:colspan 1 :token token}) ;no annotation
                    (is-B-IOB? ann token-id) (conj acc {:colspan (inc (- O B)) :token token}) ;B IOB
                    (= type "token") (conj acc {:colspan 1 :token token}) ;token
                    :else acc)));non-B IOB
          []
          hit))

(defn annotation-row [hit ann-key]
  (let [color-map (re-frame/subscribe [:filtered-users-colors])]
    (fn [{hit-id :id hit :hit} ann-key]
      (into
       [:tr.ann-row {:data-hitid hit-id}]
       (-> (for [{colspan :colspan {token-id :id anns :anns} :token} (with-colspans hit ann-key)]
             ^{:key (str ann-key hit-id token-id)}
             [annotation-cell {:ann-map (get anns ann-key)
                               :hit-id hit-id
                               :token-id token-id
                               :color-map color-map}
              colspan])
           (prepend-cell {:key (str ann-key) :child annotation-key :opts [ann-key]}))))))
