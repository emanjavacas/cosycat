(ns cleebo.annotation.components.annotation-component
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [react-bootstrap.components :as bs]
            [cleebo.utils :refer [make-ann]]
            [cleebo.autocomplete :refer [autocomplete-jq]]
            [goog.string :as gstr]))

(def cell-style
  {:width "80px"
   :padding "0px"
   :border "0px solid #eee"
   :margin "5px"})

(defn hit-row [{:keys [hit id meta]} current-token-idx]
  (fn [{:keys [hit id meta]} current-token-idx]
    (into
     [:tr]
     (for [[idx {:keys [word match anns] :as token}] (map-indexed vector hit)
           :let [info (if match "info")]]
       ^{:key (str id "-" (:id token))}
       [:td
        {:style {:cursor "pointer"}
         :class (str (if anns "has-annotation ") info)
         :on-click #(reset! current-token-idx idx)}
        word]))))

(defn parse-annotation [s]
  (let [[k v] (gstr/splitLimit s "=" 2)]
    (make-ann k v js/username)))

(defn on-key-down [id token-id]
  (fn [pressed]
    (if (= 13 (.-keyCode pressed))
      (let [ann (parse-annotation (.. pressed -target -value))]
        (set! (.-value (.-target pressed)) "") ;blankspace input
        (re-frame/dispatch
         [:annotate
          {:hit-id id
           :token-id token-id
           :ann ann}])))))

(defn focus-row [{:keys [hit id meta]}]
  (fn [{:keys [hit id meta]} current-token-idx]
    (into
     [:tr]
     (for [[idx token] (map-indexed vector hit)]
       ^{:key (str "focus-" id "-" (:id token))}
       [:td
        {:style {:padding "0px"}}
        [autocomplete-jq
         {:id (str "input-" (:id token))
          :class (str "focus-cell " (if (= @current-token-idx idx) "clicked"))
          :on-key-down (on-key-down id (:id token))
          :on-focus #(reset! current-token-idx idx)}]]))))

(defn ann-types [hit-map]
  (let [thing   (->> (mapcat :anns (:hit hit-map))
       (map :ann)
       (map :key)
       (into (hash-set)))]
    thing))

(defn annotation-component [marked-hits current-hit-idx current-token-idx]
  (fn [marked-hits current-hit-idx current-token-idx]
    (let [hit-map (nth @marked-hits @current-hit-idx)]
      [bs/table
       {:condensed true
        :responsive true
        :id "table-annotation"}
       [:thead]
       [:tbody {:style {:font-size "14px"}}
        [hit-row hit-map current-token-idx]
        [focus-row hit-map current-token-idx]
        (doall
         (for [ann (sort (map str (ann-types hit-map)))]
           ^{:key (str ann)}
           [:tr
            (doall
             (for [{:keys [id anns]} (:hit hit-map)]
               (if-let [{{key :key value :value} :ann}
                        (first
                         (filter (fn [{{key :key value :value} :ann}]
                                   (= key ann))
                                 anns))]
                 ^{:key (str ann id)} [:td [:span (str key "=" value)]]
                 ^{:key (str ann id)} [:td [:span]])))]))]])))

       ;; [:tbody {:style {:font-size "14px"}}
       ;;  ;; ^{:key (:id hit-map)}
       ;;  [hit-row hit-map current-token-idx]
       ;;  ;; ^{:key (str "f-" (:id hit-map))}
       ;;  [focus-row hit-map current-token-idx]
       ;;  ;; ^{:key (str "f-")}  
       ;;  [:tr (str (merge-annotations hit-map))]]
