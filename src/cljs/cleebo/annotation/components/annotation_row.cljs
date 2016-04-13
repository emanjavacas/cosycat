(ns cleebo.annotation.components.annotation-row
  (:require [react-bootstrap.components :as bs]
            [reagent.core :as reagent]
            [cleebo.utils :refer [parse-time ->int]]
            [schema.core :as s]
            [taoensso.timbre :as timbre]))

(defn key-val
  [{{k :key v :value} :ann user :username time :timestamp}]
  [:div k
   [:span {:style {:text-align "right" :margin-left "7px"}}
    [bs/label v]]])

(defn human-time
  [time]
  (parse-time time {"hour" "2-digit" "minute" "2-digit"}))

(defn spacer-row
  []
  [:tr {:style {:height "10px"}}])

(defn history-body [history]
  (fn [history]
    [:tbody
     (doall
      (for [{{k :key v :value :as ann} :ann
             user :username time :timestamp :as ann-map}
            (interleave (sort-by :timestamp > history) (range))
            :let [id (if ann (str v time) ann-map)]]
        (if-not ann
          ^{:key id} [spacer-row]
          ^{:key id} [:tr
                      [:td [key-val ann-map]]
                      [:td {:style {:width "25px"}}]
                      [:td
                       [:span.text-muted user]
                       [:span
                        {:style {:margin-left "10px"}}
                        (human-time time)]]])))]))

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
  [{time :timestamp user :username {k :key v :value} :ann history :history}]
  (reagent/as-component
   [bs/popover
    {:id "popover"
     :title (reagent/as-component
             [:div.container-fluid
              [:div.row.pull-right [:div.text-muted user]]
              [:br] [:br]
              [:div.row.pull-right (human-time time)]])
     :style {:max-width "100%"}}
    [:table
     (if-not (empty? history)
       [history-body history]
       [no-history-body])]]))

(defn with-overlay [& {:keys [overlay child]}]
  [bs/overlay-trigger
   {:overlay overlay
    :trigger "click"
    :rootClose true
    :placement "bottom"}
   child])

(defn style-iob
  [{time :timestamp user :username
    {k :key v :value} :ann
    {{B :B O :O} :scope} :span :as ann-map}
   token-id]
  {:border-right (if (= O token-id) "")})

(defn token-annotation-cell
  [ann-map]
  [:td.ann-cell [key-val ann-map]])

(defn span-annotation-cell
  [{{scope :scope} :span :as ann-map} token-id]
  [:td.is-span.ann-cell
   {:style (style-iob ann-map token-id)}
   [:span (when (= (:B scope) token-id) [key-val ann-map])]])

(defn annotation-cell [ann-map token-id]
  (fn [ann-map token-id]
    (let [{{type :type} :span} ann-map
          token-id (->int token-id)
          popover (user-popover ann-map)]
      (case type
        "token" [with-overlay
                 :overlay popover
                 :child (reagent/as-component (token-annotation-cell ann-map))]
        "IOB"   [with-overlay
                 :overlay popover
                 :child (reagent/as-component (span-annotation-cell ann-map token-id))]
        [:td [:span ""]]))))

(defn parse-ann-type
  [{{key :key} :ann {type :type} :span :as ann}]
  {:key key :type type})

(defn ann-types [hit-map]
  (->> (mapcat (fn [hit] (vals (:anns hit))) (:hit hit-map))
       (map parse-ann-type)
       (into (hash-set))))

(defn find-ann-by-key [by-key anns]
  (first (filter (fn [{{key :key} :ann}] (= by-key key)) anns)))

(defn annotation-rows
  "build component-fns [:tr] for each annotation in a given hit"
  [hit-map]
  (for [{key :key} (sort-by (juxt :type :key) > (ann-types hit-map))]
    [key
     (fn annotation-row-component [{hit :hit}]
       (into
        [:tr.ann-row]
        (for [{token-id :id anns :anns} hit
              :let [ann (get anns key)]]
          ^{:key (str key token-id)} [annotation-cell ann token-id])))]))
