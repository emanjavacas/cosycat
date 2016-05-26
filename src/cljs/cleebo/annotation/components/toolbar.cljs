(ns cleebo.annotation.components.toolbar
  (:require [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [cleebo.components :refer [filter-annotation-buttons]]
            [react-bootstrap.components :as bs]))

(defn close-hits-btn [open-hits]
  (fn [open-hits]
    [bs/button
     {:onClick #(reset! open-hits #{})}
     "Close hits"]))

(defn display-hits-btn [marked-hits open-hits]
  (fn [marked-hits open-hits]
    [bs/button
     {:onClick #(reset! open-hits (into #{} (map :id @marked-hits)))}
     "Display hits"]))

(defn display-buttons [marked-hits open-hits]
  (fn [marked-hits open-hits]
    [bs/button-toolbar
     [display-hits-btn marked-hits open-hits]
     [close-hits-btn open-hits]]))

(defn toolbar [marked-hits open-hits]
  (fn [marked-hits open-hits]
    [bs/navbar
     {:inverse false
      :responsive true
      :fixedTop true
      :style {:margin-top "52px" :z-index "1000"}
      :fluid true}
     [bs/nav
      {:pullRight true
       :style {:margin "5px 5px"}}
      [:li.toolbar [display-buttons marked-hits open-hits]]
      [:li.toolbar]
      [:li.toolbar [filter-annotation-buttons]]]]))
