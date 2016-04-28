(ns cleebo.annotation.components.annotation-row
  (:require [react-bootstrap.components :as bs]
            [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [cleebo.utils :refer [human-time ->int filter-dummy-tokens ->box]]
            [cleebo.components :refer [user-thumb prepend-cell dummy-cell]]
            [schema.core :as s]
            [taoensso.timbre :as timbre]))

(defn key-val
  [{{k :key v :value} :ann user :username time :timestamp}]
  [:div [bs/label k]
   [:span {:style {:text-align "right" :margin-left "7px"}}
    v]])

(defn spacer-row [] [:tr {:style {:height "10px"}}])

(defn history-body [history]
  (fn history-body [history]
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

(defn cell-style
  [color-map username]
  (if-let [color (get color-map username)]
    {:box-shadow (->box (get color-map username))}
    {:opacity "0.25"}))

(defmulti annotation-cell
  "Variadic annotation cell dispatching on span type"
  (fn [{{{span-type :type} :span} :ann-map token-id :token-id color-map :color-map}]
    span-type))

(defmethod annotation-cell "token"
  [{:keys [ann-map]}]
  (fn annotation-cell [{{username :username :as ann-map} :ann-map color-map :color-map}]
    [bs/overlay-trigger
     {:overlay (user-popover ann-map)
      :trigger "click"
      :rootClose true
      :placement "bottom"}
     [:td.ann-cell       
      {:style (cell-style @color-map username)}
      [key-val ann-map]]]))

(defmethod annotation-cell "IOB"
  [{:keys [ann-map token-id filtered-users-colors]}]
  (fn annotation-cell
    [{{{{B :B O :O} :scope} :span username :username anns :anns :as ann-map} :ann-map
      color-map :color-map token-id :token-id}]
    (timbre/debug ann-map)
    [bs/overlay-trigger
     {:overlay (user-popover ann-map)
      :trigger "click"
      :rootClose true
      :placement "bottom"}
     [:td.ann-cell
      {:style (cell-style @color-map username)}
      [:span (when (= B token-id) [key-val ann-map])]]]))

(defmethod annotation-cell :default
  [args]
  [:td ""])

(defn parse-ann-type
  "transforms an ann-map into a a map representing the ann key and the span type"
  [{{key :key} :ann {type :type} :span}]
  {:key key :type type})

(defn get-anns-in-project [anns project-name]
  (vals (get anns project-name)))

(defn ann-types
  "extracts the unique annotation keys in a given hit"
  [{hit :hit} project-name]
  (->> (mapcat (fn [{anns :anns :as token}] (get-anns-in-project anns project-name)) hit)
       (map parse-ann-type)
       (into (hash-set))))

(defn annotation-rows
  "build component-fns [:tr] for each annotation type in a given hit"
  [{hit-id :id :as hit-map} project-name]
  (for [{key :key} (sort-by (juxt :type :key) > (ann-types hit-map project-name))]
    {:key (str key hit-id)
     :component
     (fn annotation-row-component [hit open-hits & args]
       (let [users-colors (re-frame/subscribe [:filtered-users-colors])]
         (fn [{hit :hit hit-id :id} open-hits & {:keys [project-name]}]
           (into
            [:tr.ann-row]
            (-> (doall (for [{token-id :id anns :anns} (filter-dummy-tokens hit)]
                         ^{:key (str key hit-id token-id)}
                         [annotation-cell {:ann-map  (get-in anns [project-name key])
                                           :token-id (->int token-id)
                                           :color-map users-colors}]))
                (prepend-cell
                 {:key (str "dummy" hit-id)
                  :child dummy-cell}))))))}))
