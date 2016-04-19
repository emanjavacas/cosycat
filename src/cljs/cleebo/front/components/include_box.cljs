(ns cleebo.front.components.include-box
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [react-bootstrap.components :as bs]
            [cleebo.utils :refer [nbsp]]
            [taoensso.timbre :as timbre]))

(defn include-box [model selection child-component min-children]
  (fn [model selection child-component min-children]
    (let [children (concat @model (repeat (- min-children (count @model)) nil))]
      [bs/list-group
       (doall (for [[idx child-data] (map-indexed vector children)]
                (if child-data
                  ^{:key (str idx)}
                  [bs/list-group-item
                   {:onClick #(reset! selection #{child-data})
                    :style {:min-height "50px"}}
                   (reagent/as-component [:div.text-center [child-component child-data]])]
                  ^{:key (str idx)}
                  [bs/list-group-item
                   {:style {:min-height "50px"}}
                   (nbsp 10)])))])))

(defn left-right-click
  [target-model source-model source-selection]
  (fn [event]
    (.preventDefault event)
    (swap! target-model clojure.set/union @source-selection)
    (swap! source-model clojure.set/difference @source-selection)
    (reset! source-selection #{})))

(defn include-box-component [{:keys [model on-select child-component]}]
  (let [model-left (reagent/atom #{})
        model-right (reagent/atom (apply hash-set model))
        selection-left (reagent/atom #{})
        selection-right (reagent/atom #{})
        _ (add-watch model-left :sel (fn [_ _ _ new-state] (on-select new-state)))]
    (fn [{:keys [model selection-atom]}]
      (let [nchildren (count @model-right)]
        [:div.container-fluid
         [:div.row
          [:div.col-lg-5
           [include-box model-left selection-left child-component nchildren]
           [:span.text-muted.pull-right
            [bs/label "Selected users"]]]
          [:div.col-lg-2.text-center
           {:style {:margin-top (str (* 11 nchildren) "px")}} ;dirty fix
           [:div.row
            [bs/button
             {:onClick (left-right-click model-left model-right selection-right)}
             [bs/glyphicon {:glyph "chevron-left"}]]]
           [:div.row
            [bs/button
             {:onClick (left-right-click model-right model-left selection-left)}
             [bs/glyphicon {:glyph "chevron-right"}]]]]
          [:div.col-lg-5
           [include-box model-right selection-right child-component nchildren]
           [:span.text-muted.pull-right
            [bs/label "Available users"]]]]]))))

