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
  [color-map username highlight?]
  (let [color (get color-map username)]
    (cond-> {}
      highlight?  (assoc :background-color "antiquewhite")
      (not color) (assoc :opacity "0.25")
      color       (assoc :box-shadow (->box color)))))

(defn annotation-cell
  [ann-map hit-id token-id colspan color-map & {:keys [editable? highlight?]}]
  (let [open? (reagent/atom false), target (reagent/atom nil)]
    (fn [{username :username anns :anns :as ann-map}
         hit-id token-id colspan color-map & {:keys [editable? highlight?]}]
      [:td.ann-cell
       {:style (annotation-cell-style @color-map username highlight?)
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

(defn cell-colspan [token-id token-from token-to {{{B :B O :O} :scope type :type} :span :as ann}]
  (let [{:keys [id]} (parse-token-id token-id)]
    (cond
      ;; missing annotation
      (not ann)        1
      ;; IOB annotation
      (and (= type "IOB") (= B id)) (inc (- (min token-to O) (max token-from B)))
      (and (= type "IOB") (= token-from id)) (inc (- O token-from))
      (= type "IOB") nil
      ;; token annotation
      (= type "token") 1)))

(defn with-colspans
  "compute colspan for a given annotation"
  [hit ann-key]
  (let [token-from (-> hit first :id parse-token-id :id)
        token-to (-> hit last :id parse-token-id :id)]
    (reduce (fn [acc {token-id :id anns :anns :as token}]
              (if-let [colspan (cell-colspan token-id token-from token-to (get anns ann-key))]
                (conj acc {:colspan colspan :token token})
                acc))
            []
            hit)))

(defn highlight-cell? [ann-key token-id highlight-ann-key? highlight-token-id?]
  (cond
    (and highlight-ann-key? highlight-token-id?)
    (and (= highlight-ann-key? ann-key) (= highlight-token-id? token-id))
    highlight-ann-key? (= highlight-ann-key? ann-key)
    highlight-token-id? (= highlight-token-id? token-id)))

(defn annotation-row
  [hit color-map ann-key & {:keys [editable? highlight-ann-key? highlight-token-id?]}]
  (fn [{hit-id :id hit :hit} color-map ann-key
       & {:keys [editable? highlight-ann-key? highlight-token-id?] :or {editable? true}}]
    (into
     [:tr.ann-row {:data-hitid hit-id}]
     (-> (for [{colspan :colspan {token-id :id anns :anns} :token} (with-colspans hit ann-key)
               :let [{parsed-token-id :id} (parse-token-id token-id)]]
           ^{:key (str ann-key hit-id token-id)}
           (if-let [ann-map (get anns ann-key)]
             [annotation-cell
              (get anns ann-key)
              hit-id
              token-id
              colspan
              color-map
              :editable? editable?
              :highlight?
              (highlight-cell? ann-key parsed-token-id highlight-ann-key? highlight-token-id?)]
             [dummy-cell]))
         (prepend-cell {:key (str ann-key) :child annotation-key :opts [ann-key]})))))
