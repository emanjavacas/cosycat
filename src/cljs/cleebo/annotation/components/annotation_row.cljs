(ns cleebo.annotation.components.annotation-row
  (:require [react-bootstrap.components :as bs]
            [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [cleebo.utils :refer [human-time ->int filter-dummy-tokens]]
            [cleebo.components :refer [user-thumb]]
            [schema.core :as s]
            [taoensso.timbre :as timbre]))

(defn key-val
  [{{k :key v :value} :ann user :username time :timestamp}]
  [:div k
   [:span {:style {:text-align "right" :margin-left "7px"}}
    [bs/label v]]])

(defn spacer-row [] [:tr {:style {:height "10px"}}])

(defn ->box [color] (str "0 -3px " color " inset"))

(defn history-body [history]
  (fn [history]
    [:tbody
     (doall
      (for [{{v :value} :ann user :username time :timestamp} (sort-by :timestamp > history)]
        ^{:key (str v time)}
        [:tr {:style {:padding "50px"}}
         [:td [bs/label v]]
         [:td {:style {:width "25px"}}]
         [:td
          [:span.text-muted user]
          [:span
           {:style {:margin-left "10px"}}
           (human-time time)]]]))]))

(defn no-history-body []
  (let [editing? (reagent/atom false)]
    (fn []
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

(defmulti annotation-cell
  "variadic annotation cell dispatching on span type"
  (fn [{:keys [ann-map token-id]}]
    (let [{{span-type :type} :span} ann-map]
      span-type)))

(defmethod annotation-cell "token"
  [{:keys [ann-map]}]
  (fn [{{username :username :as ann-map} :ann-map
        filtered-users-colors :filtered-users-colors}]
    [bs/overlay-trigger
     {:overlay (user-popover ann-map)
      :trigger "click"
      :rootClose true
      :placement "bottom"}
     [:td.ann-cell       
      {:style {:box-shadow (->box (get @filtered-users-colors username))}}
      [key-val ann-map]]]))

(defmethod annotation-cell "IOB"
  [{:keys [ann-map token-id filtered-users-colors]}]
  (fn [{{{{B :B O :O} :scope} :span username :username :as ann-map} :ann-map
        filtered-users-colors :filtered-users-colors
        token-id :token-id}]
    [bs/overlay-trigger
     {:overlay (user-popover ann-map)
      :trigger "click"
      :rootClose true
      :placement "bottom"}
     [:td.ann-cell
      {:style {:box-shadow (->box (get @filtered-users-colors username))}}
      [:span (when (= B token-id) [key-val ann-map])]]]))

(defmethod annotation-cell :default
  [args]
  [:td ""])

(defn parse-ann-type
  "transforms an ann-map into a a map representing the ann key and the span type"
  [{{key :key} :ann {type :type} :span}]
  {:key key :type type})

(defn ann-types
  "extracts the unique annotation keys in a given hit"
  [hit-map]
  (->> (mapcat (fn [hit] (vals (:anns hit))) (:hit hit-map))
       (map parse-ann-type)
       (into (hash-set))))

(defn annotation-rows
  "build component-fns [:tr] for each annotation in a given hit"
  [hit-map]
  (for [{key :key} (sort-by (juxt :type :key) > (ann-types hit-map))]
    [key
     (fn annotation-row-component [{hit :hit}]
       (let [filtered-users-colors (re-frame/subscribe [:filtered-users-colors])]
         (into
          [:tr.ann-row]
          (for [{token-id :id anns :anns} (filter-dummy-tokens hit)
                :let [ann-map (get anns key)
                      token-id (->int token-id)]]
            ^{:key (str key token-id)}
            [annotation-cell
             {:ann-map ann-map
              :token-id token-id
              :filtered-users-colors filtered-users-colors}]))))]))
