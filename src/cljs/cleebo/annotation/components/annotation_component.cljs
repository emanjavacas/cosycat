(ns cleebo.annotation.components.annotation-component
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [react-bootstrap.components :as bs]
            [cleebo.utils :refer
             [parse-annotation dispatch-annotation dispatch-span-annotation ->int]]
            [cleebo.autocomplete :refer [autocomplete-jq]]
            [goog.string :as gstr]
            [goog.dom.dataset :as gdataset]
            [taoensso.timbre :as timbre]
            [cljs.core.match :refer-macros [match]]))

(def cell-style
  {:width "80px"
   :padding "0px"
   :border "0px solid #eee"
   :margin "5px"})

(defn hit-row [{:keys [hit id meta]} current-token-idx]
  (fn [{:keys [hit id meta]} current-token-idx]
    (into
     [:tr
      {:on-mouse-down #(.log js/console %)}]
     (for [[idx {:keys [word match anns] :as token}] (map-indexed vector hit)
           :let [info (if match "info")]]
       ^{:key (str id "-" (:id token))}
       [:td
        {:class (str (if anns "has-annotation ") info)}
        word]))))

(defn get-token-ids
  [from to hit]
  (->> (subvec (vec hit) from (inc to))
       (map :id)
       (map ->int)))

(defn on-key-down [id token-id hit ctrl-down? current-token-idx span]
  (fn [pressed]
    (case (.-keyCode pressed)
      9 (reset! span nil)            ;tab
      17 (swap! ctrl-down? not)        ;ctrl
      13 (if-let [[k v] (parse-annotation (.. pressed -target -value))]
           (do (if-not @span
                 (dispatch-annotation k v (->int id) (->int token-id))
                 (let [[from to] (sort [@current-token-idx @span])
                       token-ids (get-token-ids from to hit)]
                   (dispatch-span-annotation k v (->int id) token-ids)))
               (set! (.-value (.-target pressed)) ""))); blankspace input
      nil)))

(defn on-key-up [ctrl-down?]
  (fn [pressed]
    (when (and @ctrl-down? (= 17 (.-keyCode pressed)))
      ;dirty fix
      (js/setTimeout #(swap! ctrl-down? not) 500))))

(defn on-click [span ctrl-down?]
  (fn [event]
    (if-not @ctrl-down?
      (reset! span nil)
      (let [id (js/parseInt (gdataset/get (aget event "target") "id"))]
        (reset! span id)))))

(defn on-focus [current-token-idx idx ctrl-down?]
  (fn []
    (when-not @ctrl-down?
      (reset! current-token-idx idx))))

(defn is-clicked? [idx current-token-idx span]
  (cond (and (not @span) (= idx @current-token-idx)) "clicked"
        (and @span (or (and (>= idx @current-token-idx) (<= idx @span))
                      (and (<= idx @current-token-idx) (>= idx @span))))
        "clicked"
        :else ""))

(defn focus-row [{:keys [hit id meta]} current-token-idx]
  (let [span (reagent/atom nil)
        ctrl-down? (reagent/atom false)]
    (fn [{:keys [hit id meta]} current-token-idx]
      (into
       [:tr]
       (for [[idx token] (map-indexed vector hit)
             :let [token-id (:id token)]]
         ^{:key (str "focus-" id "-" token-id)}
         [:td
          {:style {:padding "0px"}}
          [autocomplete-jq
           {:source :complex-source
            :id (str "input-" token-id)
            :data-id idx
            :class (str "focus-cell " (is-clicked? idx current-token-idx span))
            :on-key-down (on-key-down id token-id hit ctrl-down? current-token-idx span)
            :on-key-up (on-key-up ctrl-down?)
            :on-click (on-click span ctrl-down?)
            :on-focus (on-focus current-token-idx idx ctrl-down?)}]])))))

(defn ann-types [hit-map]
  (let [thing   (->> (mapcat :anns (:hit hit-map))
       (map :ann)
       (map :key)
       (into (hash-set)))]
    thing))

(defn find-ann-by-key [by-key anns]
  (first (filter (fn [{{key :key} :ann}] (= by-key key)) anns)))

(defn style-iob [{key :key {value :value IOB :IOB} :value}]
  (let [background (case IOB
                     "I" "#e8f2eb"
                     "B" "#d8e9dd"
                     "O" "#d8e9dd"
                     "white")]
    [:td {:style {:background-color background}
          :class "is-span"}
     [:span (when (= "B" IOB) (str key "=" value))]]))

(defn annotation-cell [ann]
  (fn [ann]
    (let [{timestamp :timestamp username :username {key :key value :value} :ann} ann
          span (cond (string? value)  [:td [:span (str key "=" value)]]
                     (map? value)     (style-iob (:ann ann))
                     (nil? value)     [:td [:span ""]])]
      span)))

(defn annotation-component [marked-hits current-hit-idx current-token-idx]
  (fn [marked-hits current-hit-idx current-token-idx]
    (let [hit-map (nth @marked-hits @current-hit-idx)]
      [bs/table
       {;:condensed true
        :responsive true
        :striped true
        :id "table-annotation"}
       [:thead]
       [:tbody {:style {:font-size "14px"}}
        [hit-row hit-map current-token-idx]
        [focus-row hit-map current-token-idx]
        (doall
         (for [key (sort (map str (ann-types hit-map)))]
           ^{:key (str key)}
           [:tr
            (doall
             (for [{:keys [id anns]} (:hit hit-map)]
               (let [{{value :value} :ann :as ann} (find-ann-by-key key anns)]
                 ^{:key (str key id)} [annotation-cell ann])))]))]])))

