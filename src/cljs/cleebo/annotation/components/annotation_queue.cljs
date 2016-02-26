(ns cleebo.annotation.components.annotation-queue
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [react-bootstrap.components :as bs]))

(def current-hit-style
  {:border "1px solid #ddd"
   :background-color "#f5f5f5"})

(def default-style
  {:background-color "#fcfcfd"})

(defn annotation-queue-row [{:keys [hit meta id]} is-current-hit]
  (fn [{:keys [hit meta id]} is-current-hit]
    [:tr
     {:class (if is-current-hit "checked")
      :style (if is-current-hit current-hit-style default-style)}
     (concat
      [^{:key (str "pre" id)}
       [:td (if is-current-hit [:i.zmdi.zmdi-caret-right-circle
                                {:style {:line-height "1.6em"}}])]]
      (for [{:keys [word id]} hit]
        ^{:key id} [:td word]))]))

(defn annotation-queue [marked-hits current-hit]
  (fn [marked-hits current-hit]
    [bs/table
     {:style {:font-size "14px"}
      :responsive true
      :striped true
      :id "table-queue"}
     [:thead]
     [:tbody
      (doall
       (for [[idx {:keys [hit id] :as hit-map}] (map-indexed vector @marked-hits)
             :let [is-current-hit (= idx @current-hit)]]
         ^{:key id} [annotation-queue-row hit-map is-current-hit]))]]))

