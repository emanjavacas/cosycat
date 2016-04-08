(ns cleebo.annotation.components.annotation-row
  (:require [react-bootstrap.components :as bs]
            [reagent.core :as reagent]
            [cleebo.utils :refer [parse-time]]))

(defn user-popover [user time]
  (reagent/as-component
   [bs/popover
    {:id "popover"
     :title user}
    [:div [:span (parse-time time)]]]))

(defn key-val [k v user time]
  [:div k
   [bs/overlay-trigger
    {:overlay (user-popover user time)
     :placement "right"}      
    [:span {:style {:text-align "right" :margin-left "7px"}} [bs/label v]]]])

(defn style-iob [{key :key {value :value IOB :IOB} :value user :username time :timestamp}]
  (let [background (case IOB
                     "I" "#e8f2eb"
                     "B" "#d8e9dd"
                     "O" "#d8e9dd"
                     "white")]
    [:td.is-span.ann-cell {:style {:background-color background}}
     [:span (when (= "B" IOB) [key-val key value user time])]]))

(defn annotation-cell [ann]
  (fn [ann]
    (let [{time :timestamp user :username {key :key value :value} :ann} ann
          span (cond (string? value)  [:td.ann-cell [key-val key value user time]]
                     (map? value)     (style-iob (:ann ann))
                     (nil? value)     [:td [:span ""]])]
      span)))

(defn ann-types [hit-map]
  (->> (mapcat :anns (:hit hit-map))
       keys
       (into (hash-set))))

(defn find-ann-by-key [by-key anns]
  (first (filter (fn [{{key :key} :ann}] (= by-key key)) anns)))

(defn annotation-rows
  "build component-fns [:tr] for each annotation in a given hit"
  [hit-map]
  (for [key (sort (map str (ann-types hit-map)))]
    [key
     (fn annotation-row-component [{:keys [hit]}]
       (into
        [:tr]
        (for [{:keys [id anns]} hit
              :let [ann (get anns key)]]
          ^{:key (str key id)} [annotation-cell ann])))]))
