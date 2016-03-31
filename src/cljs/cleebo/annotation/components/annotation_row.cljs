(ns cleebo.annotation.components.annotation-row
  (:require [react-bootstrap.components :as bs]))

(defn key-val [k v]
  [:table {:width "100%"}
   [:tbody
    [:tr
     [:td {:style {:text-align "left"}} k]
     [:td {:style {:text-align "right"}} [bs/label v]]]]]
;  [:span (str k "=" v)]
  )

(defn style-iob [{key :key {value :value IOB :IOB} :value}]
  (let [background (case IOB
                     "I" "#e8f2eb"
                     "B" "#d8e9dd"
                     "O" "#d8e9dd"
                     "white")]
    [:td {:style {:background-color background}
          :class "is-span"}
     [:span (when (= "B" IOB) [key-val key value])]]))

(defn annotation-cell [ann]
  (fn [ann]
    (let [{timestamp :timestamp username :username {key :key value :value} :ann} ann
          span (cond (string? value)  [:td [key-val key value]]
                     (map? value)     (style-iob (:ann ann))
                     (nil? value)     [:td [:span ""]])]
      span)))

(defn ann-types [hit-map]
  (let [thing   (->> (mapcat :anns (:hit hit-map))
       (map :ann)
       (map :key)
       (into (hash-set)))]
    thing))

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
        (for [{:keys [id anns]} hit]
          (let [ann (find-ann-by-key key anns)]
            ^{:key (str key id)} [annotation-cell ann]))))]))
