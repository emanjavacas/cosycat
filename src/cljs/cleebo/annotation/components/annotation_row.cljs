(ns cleebo.annotation.components.annotation-row
  (:require [react-bootstrap.components :as bs]
            [reagent.core :as reagent]
            [cleebo.utils :refer [parse-time]]
            [schema.core :as s]))

(defn user-popover [user time]
  (reagent/as-component
   [bs/popover
    {:id "popover"
     :title user}
    [:div [:span (parse-time time {"hour" "2-digit" "minute" "2-digit"})]]]))

(defn key-val [k v user time]
  [:div k
   [bs/overlay-trigger
    {:overlay (user-popover user time)
     :placement "right"}      
    [:span {:style {:text-align "right" :margin-left "7px"}} [bs/label v]]]])

(s/defn ^:always-validate style-iob
  [{time :timestamp user :username
    {k :key v :value} :ann
    {{B :B O :O} :scope} :span}
   token-id]
  (let [background (condp = token-id
                     (str B) "#d8e9dd"
                     (str O) "#d8e9dd"
                     "#e8f2eb")]
    [:td.is-span.ann-cell {:style {:background-color background}}
     [:span (when (= (str B) token-id) [key-val k v user time])]]))

(defn annotation-cell [ann-map token-id]
  (fn [ann-map token-id]
    (let [{time :timestamp user :username
           {k :key v :value} :ann
           {t :type} :span} ann-map]
      (case t
        "token"  [:td.ann-cell [key-val k v user time]]
        "IOB"     (style-iob ann-map token-id)
        [:td [:span ""]]))))

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
     (fn annotation-row-component [& {{hit :hit} :hit-map}]
       (into
        [:tr.ann-row]
        (for [{token-id :id anns :anns} hit
              :let [ann (get anns key)]]
          ^{:key (str key token-id)} [annotation-cell ann token-id])))]))
