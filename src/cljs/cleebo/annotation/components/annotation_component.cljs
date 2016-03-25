(ns cleebo.annotation.components.annotation-component
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [react-bootstrap.components :as bs]
            [cleebo.utils :refer
             [parse-annotation dispatch-annotation dispatch-span-annotation ->int]]
            [cleebo.autocomplete :refer [autocomplete-jq]]
            [goog.string :as gstr]
            [goog.dom.dataset :as gdataset]
            [taoensso.timbre :as timbre]))

(defn key-val [k v]
  ;; [:table {:width "100%"}
  ;;  [:tbody
  ;;   [:tr
  ;;    [:td {:style {:text-align "left"}} k]
  ;;    [:td {:style {:text-align "right"}} [bs/label v]]]]]
  [:span (str k "=" v)]
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

(defn hit-row [{:keys [hit id meta]}]
  (fn [{:keys [hit id meta]}]
    (into
     [:tr
      {:style {:background-color "#ccccaa"}}]
     (for [[idx {:keys [word match anns] :as token}] (map-indexed vector hit)
           :let [info (if match "info")]]
       ^{:key (str id "-" (:id token))}
       [:td
        {:class (str (if anns "has-annotation ") info)}
        word]))))

(defn on-key-down [id token-id]
  (fn [pressed]
    (if (= 13 (.-keyCode pressed))
      (if-let [[k v] (parse-annotation (.. pressed -target -value))]
        (do
          (dispatch-annotation k v (->int id) (->int token-id))
          (set! (.-value (.-target pressed)) ""))))))

(defn focus-row [{:keys [hit id meta]}]
  (fn [{:keys [hit id meta]}]
    (into
     [:tr
      {:style {:background-color "#aacccc"}}]
     (for [[idx token] (map-indexed vector hit)
           :let [token-id (:id token)]]
       ^{:key (str "focus-" id "-" token-id)}
       [:td
        {:style {:padding "0px"}}
        [autocomplete-jq
         {:source :complex-source
          :id (str "input-" token-id)
          :data-id idx
          :class "focus-cell"
          :on-key-down (on-key-down id token-id)}]]))))

(defn ann-types [hit-map]
  (let [thing   (->> (mapcat :anns (:hit hit-map))
       (map :ann)
       (map :key)
       (into (hash-set)))]
    thing))

(defn find-ann-by-key [by-key anns]
  (first (filter (fn [{{key :key} :ann}] (= by-key key)) anns)))

(defn annotation-rows [hit-map]
  (for [key (sort (map str (ann-types hit-map)))]
    [key
     (fn annotation-row-component [{:keys [hit]}]
       (into
        [:tr]
        (for [{:keys [id anns]} hit]
          (let [ann (find-ann-by-key key anns)]
            ^{:key (str key id)} [annotation-cell ann]))))]))

(defn queue-row [current-hit-id]
  (fn [{:keys [hit id]}]
    (into
     [:tr
      {:style {:background-color "#f5f5f5"
               :cursor "pointer"}
       :class "queue-row"
       :on-click #(reset! current-hit-id id)}]
     (for [{:keys [word id]} hit]
       ^{:key id} [:td word]))))

(defn get-target-hit-id
  [marked-hits current-ann-hit-id]
  (if-not current-ann-hit-id
    (:id (first marked-hits))
    current-ann-hit-id))

(defn rows [{:keys [id] :as hit-map} marked-hits current-hit-id]
  (let [target-hit-id (get-target-hit-id @marked-hits @current-hit-id)]
    (if (= id target-hit-id)
      (concat
       [["hit"   hit-row]
        ["focus" focus-row]]
       (annotation-rows hit-map))
      [[(str "row-" id) (queue-row current-hit-id)]])))

(defn annotation-component [marked-hits]
  (let [current-hit-id (reagent/atom nil)]
    (fn [marked-hits]
      [bs/table
       {:responsive true
        :id "table-annotation"}
       [:thead]
       [:tbody {:style {:font-size "14px"}}
        (doall
         (for [hit-map @marked-hits
               [id row-fn] (rows hit-map marked-hits current-hit-id)]
           ^{:key id} [row-fn hit-map]))]])))
