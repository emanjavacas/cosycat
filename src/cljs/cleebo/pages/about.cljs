(ns cleebo.pages.about
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]))


(defn about-title []
  [:h1 "This is the About Page."])

(defn link-to-home-page []
  [:a {:href "#/"} "go to the Home Page"])

(defn about-panel []
  [:div.container
   [:div.row
    [about-title]
    [link-to-home-page]]])
