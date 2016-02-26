(ns cleebo.annotation.components.annotation-row
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [react-bootstrap.components :as bs]
            [cleebo.shared-schemas :refer [make-ann]]
            [goog.string :as gstr]))

(def cell-style
  {:width "80px"
   :padding "0px"
   :border "0px solid #eee"
   :margin "5px"})

(defn hit-row [{:keys [hit id meta]} current-token]
  (fn [{:keys [hit id meta]} current-token]
    (into
     [:tr]
     (for [[idx {:keys [word match anns] :as token}] (map-indexed vector hit)
           :let [info (if match "info")]]
       ^{:key (str id "-" (:id token))}
       [:td
        {:style {:cursor "pointer"}
         :class (str (if anns "has-annotation "))
         :on-hover #()
         :on-click #(reset! current-token idx)}
        word]))))

(defn parse-annotation [s]
  (let [[k v] (gstr/splitLimit s "=" 2)]
    (make-ann {k v} js/username)))

(defn focus-row [{:keys [hit id meta]} current-token]
  (fn [{:keys [hit id meta]} current-token]
    (into
     [:tr]
     (for [[idx token] (map-indexed vector hit)]
       ^{:key (str "focus-" id "-" (:id token))}
       [:td
        {:style {:padding "0px"}}
        [:input
         {:class (str "focus-cell " (if (= @current-token idx) "clicked"))
          :on-key-down
          (fn [pressed]
            (if (= 13 (.-keyCode pressed))
              (let [ann (parse-annotation (.. pressed -target -value))]
                (re-frame/dispatch
                 [:annotate
                  {:hit-id id
                   :token-id (:id token)
                   :ann ann}]))))
          :on-focus #(reset! current-token idx)}]]))))

(defn annotation-row [marked-hits current-hit current-token]
  (fn [marked-hits current-hit current-token]
    (let [hit-map (nth @marked-hits @current-hit)]
      [bs/table
       {;:bordered true
        :condensed true
        :responsive true
        :id "table-annotation"}
       [:thead]
       [:tbody {:style {:font-size "14px"}}
        ^{:key (:id hit-map)}            [hit-row hit-map current-token]
        ^{:key (str "f-" (:id hit-map))} [focus-row hit-map current-token]]])))
