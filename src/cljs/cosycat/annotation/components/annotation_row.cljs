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

(defn annotation-cell [ann-map hit-id token-id colspan color-map & {:keys [editable?]}]
  (let [open? (reagent/atom false), target (reagent/atom nil)]
    (fn [{username :username anns :anns :as ann-map}
         hit-id token-id colspan color-map & {:keys [editable?]}]
      [:td.ann-cell
       {:style (annotation-cell-style @color-map username)
        :colSpan colspan
        :on-click #(do (reset! target (.-target %)) (swap! open? not))}
       [:div
        [key-val ann-map]
        [bs/overlay
         {:show @open?
          :target (fn [] @target) ;DOMNode
          :rootClose true
          :onHide #(swap! open? not) ;called when rootClose triggers
          :placement "top"}
         (annotation-popover
          {:ann-map ann-map
           :hit-id hit-id
           :on-dispatch #(swap! open? not)
           :editable? editable?})]]])))

(defn annotation-key [key]
  [:td [bs/label {:style {:font-size "90%"}} key]])

(defn is-B-IOB? [{{{B :B O :O} :scope type :type} :span} token-id]
  (and (= type "IOB") (= B (-> (parse-token-id token-id) :id))))

(defn cell-colspan [token-id {{{B :B O :O} :scope type :type} :span :as ann}]
  (cond (not ann)                1 ;; no annotation
        (is-B-IOB? ann token-id) (inc (- O B)) ;; B IOB
        (= type "token")         1)) ;; token

(defn with-colspans
  "compute colspan for a given annotation"
  [hit ann-key]
  (reduce (fn [acc {token-id :id anns :anns :as token}]
            (if-let [colspan (cell-colspan token-id (get anns ann-key))]
              (conj acc {:colspan colspan :token token})
              acc))
          []
          hit))

(defn annotation-row [hit ann-key & {:keys [editable?] :or {editable? true}}]
  (let [color-map (re-frame/subscribe [:filtered-users-colors])]
    (fn [{hit-id :id hit :hit} ann-key]
      (into
       [:tr.ann-row {:data-hitid hit-id}]
       (-> (for [{colspan :colspan {token-id :id anns :anns} :token} (with-colspans hit ann-key)]
             ^{:key (str ann-key hit-id token-id)}
             (if-let [ann-map (get anns ann-key)]
               [annotation-cell
                (get anns ann-key)
                hit-id
                token-id
                colspan
                color-map
                :editable? editable?]
               [dummy-cell]))
           (prepend-cell {:key (str ann-key) :child annotation-key :opts [ann-key]}))))))
