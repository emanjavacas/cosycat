(ns cleebo.annotation.page
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [react-bootstrap.components :as bs]
            [cleebo.components :refer [error-panel]]))

(defn hit-token [{:keys [id word match marked]}]
  (fn [{:keys [id word match marked]}]
    (let [info (if match "info" "")]
      [:td.highlighted {:class (str info) :data-id id}  word
;       [:span {:class "hint--bottom" :data-hint "Hi there!"} ""]
       ])))

(defn hit-row [hit-num hit]
  (fn [hit-num hit]
    (into [:tr]
          (for [token hit]
            ^{:key (str hit-num "-" (:id token))} [hit-token token]))))

(def example-anns [{:key "animacy" :value "M"} {:key "aspect" :value "perfect"}])

(defn new-annotation-button []
  [bs/button
   {:bsClass "info"
    :on-click #(.log js/console "NEW")}
   [bs/glyphicon {:glyph "plus"}]])

(defn annotation-row [hit hit-num]
  (fn [hit hit-num]
    (into
     [:tr]
     (for [{:keys [id xmlid word ann] :as token} hit
           :let [key (str hit-num "-" id "props")]]
       ^{:key key}
       [:td {:style {:max-width "65px"}}
        [:table {:style {:font-size "13px" :max-width "100%" :min-height "50px"}}
         [:thead]
         [:tbody
          (if ann
            (for [[k v] (seq ann)]
              ^{:key (str hit-num "-" id "-anns-" k)}
              [:tr {:style {:padding "15px"}}
               [:td {:style {:text-align "left"  :padding-top "5px" :padding-bottom "5px"}} (str k)]
               [:td {:style {:text-align "right" :padding-top "5px" :padding-bottom "5px"}}
                [bs/label v]]])
            [:tr])]]]))))

(defn annotation-queue [marked-hits]
  (fn [marked-hits]
    [bs/table
     {:responsive true
      :bordered true
      :id "tableAnnotation"}
     [:thead]
     [:tbody {:style {:font-size "13px"}}
      (doall
       (interleave
        (for [[hit-num {:keys [hit meta]}] (sort-by first @marked-hits)]
          ^{:key hit-num} [hit-row hit-num hit])
        (for [[hit-num {:keys [hit meta]}] (sort-by first @marked-hits)]
          ^{:key (str hit-num "anns")} [annotation-row hit hit-num])))]]))

(defn back-to-query-button []
  [bs/button {:href "#/query"}
   [:span {:style {:padding-right "10px"}}
    [:i.zmdi.zmdi-city-alt]]
   "Back to query"])

(defn annotation-panel []
  (let [marked-hits (re-frame/subscribe [:marked-hits])]
    (fn []
      [:div.container
       {:style {:width "100%" :padding "0 10px 0 10px"}}
       (if (zero? (count @marked-hits))
         [error-panel
          :status "No hits marked for annotation..."
          :status-content [back-to-query-button]]
         [annotation-queue marked-hits])])))

