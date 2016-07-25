(ns cleebo.annotation.components.annotation-row
  (:require [react-bootstrap.components :as bs]
            [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [cleebo.utils :refer [human-time ->int filter-dummy-tokens ->box]]
            [cleebo.components :refer [user-thumb prepend-cell]]
            [taoensso.timbre :as timbre]))

(defn key-val
  [{{k :key v :value} :ann user :username time :timestamp}]
  [:div
   [:span {:style {:text-align "right" :margin-left "7px"}} [bs/label v]]])

(defn history-body [history]
  (fn [history]
    [:tbody
     (doall
      (for [{{value :value} :ann :as ann} (sort-by :timestamp > history)]
        ^{:key (str value (:timestamp ann))}
        [:tr {:style {:padding "50px"}}
         [:td [bs/label value]]
         [:td {:style {:width "25px"}}]
         [:td
          [:span.text-muted (:username ann)]
          [:span
           {:style {:margin-left "10px"}}
           (human-time (:timestamp ann))]]]))]))

(defn no-history-body []
  (let [editing? (reagent/atom false)]
    (fn no-history-body []
      [:tbody
       [:tr
        {:on-click #(swap! editing? not)}
        (if @editing?
          [:td [:input]]
          [:td "No annotation history"])]])))

(defn user-popover
  [{time :timestamp username :username {k :key v :value} :ann history :history}]
  (let [user (re-frame/subscribe [:user username])]
    (reagent/as-component
     [bs/popover
      {:id "popover"
       :title (reagent/as-component
               [:div.container-fluid
                [:div.row
                 [:div.col-sm-4
                  [user-thumb (get-in @user [:avatar :href])]]
                 [:div.col-sm-8
                  [:div.row.pull-right [:div.text-muted username]]
                  [:br] [:br]
                  [:div.row.pull-right (human-time time)]]]])
       :style {:max-width "100%"}}
      [:table
       (if-not (empty? history)
         [history-body history]
         [no-history-body])]])))

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
     {:overlay (user-popover ann-map)
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
     {:overlay (user-popover ann-map)
      :trigger "click"
      :rootClose true
      :placement "bottom"}
     [:td.ann-cell
      {:style (annotation-cell-style @color-map username)}
      [:span (when (= B (->int token-id)) [key-val ann-map])]]]))

(defmethod annotation-cell :default [_] [:td ""])

(defn ann-key-cell [ann-key] [:td ann-key])

(defn annotation-row [hit ann-key]
  (let [color-map (re-frame/subscribe [:filtered-users-colors])]
    (fn [{hit-id :id hit :hit} ann-key]
      (into
       [:tr.ann-row]
       (-> (for [{token-id :id anns :anns} (filter-dummy-tokens hit)]
             ^{:key (str ann-key hit-id token-id)}
             [annotation-cell {:ann-map (get anns ann-key)
                               :token-id token-id
                               :color-map color-map}])
           (prepend-cell {:key (str ann-key) :child ann-key-cell :opts [ann-key]}))))))
