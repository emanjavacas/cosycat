(ns cleebo.annotation.components.annotation-component
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [react-bootstrap.components :as bs]
            [cleebo.utils :refer
             [parse-annotation dispatch-annotation dispatch-span-annotation ->int]]
            [cleebo.autocomplete :refer [autocomplete-jq]]
            [goog.string :as gstr]
            [goog.dom.dataset :as gdataset]
            [goog.dom.classes :as gclass]
            [taoensso.timbre :as timbre]))

(defn key-val [k v]
  ;; [:table {:width "100%"}
  ;;  [:tbody
  ;;   [:tr
  ;;    [:td {:style {:text-align "left"}} k]
  ;;    [:td {:style {:text-align "right"}} [bs/label v]]]]]
  [:span (str k "=" v)])

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

(defn token-cell [token & [props]]
  (fn [token]
    (let [{:keys [word match anns]} token]
      [:td
       (merge props {:class (str (when anns "has-annotation ") (when match "info"))})
       word])))

(defn hit-row [{:keys [hit id meta]}]
  (fn [{:keys [hit id meta]}]
    (into
     [:tr
      {:style {:background-color "#cedede"}}]
     (for [token hit]
       ^{:key (str id "-" (:id token))} [token-cell token]))))

(defn on-key-down [id token-id]
  (fn [pressed]
    (if (= 13 (.-keyCode pressed))
      (if-let [[k v] (parse-annotation (.. pressed -target -value))]
        (do
          (dispatch-annotation k v (->int id) (->int token-id))
          (set! (.-value (.-target pressed)) ""))))))

(defn on-mouse-down [mouse-down? highlighted? selection id]
  (fn [event]
    (.log js/console @selection)
    (let [e (aget event "target")]
      (if @mouse-down? (.preventDefault event))
      (gclass/toggle e "highlighted")
      (swap! mouse-down? not)
      (reset! highlighted? (gclass/has e "highlighted"))
      (if @highlighted?
        (swap! selection conj id)
        (swap! selection disj id)))))

(defn on-mouse-over [mouse-down? highlighted? selection id]
  (fn [event]
    (let [e (aget event "target")]
      (when @mouse-down?
        (gclass/enable e "highlighted" @highlighted?)
        (if @highlighted?
          (swap! selection conj id)
          (swap! selection disj id))))))

(defn on-mouse-up [mouse-down?]
  (fn [event]
    (swap! mouse-down? not)))

(defn input-row
  "component for the input row"
  [{:keys [hit id meta]}]
  (let [mouse-down? (reagent/atom false)
        highlighted? (reagent/atom false)
        selection (reagent/atom #{})]
    (fn [{:keys [hit id meta]}]
      (into
       [:tr
        {:style {:box-shadow "2px 2px 2px 0px rgba(0, 0, 0, 0.5)"}}]
       (for [[idx token] (map-indexed vector hit)
             :let [token-id (:id token)]]
         ^{:key (str "input-" id "-" token-id)}
         [:td
          {:style {:padding "0px"}}
          [autocomplete-jq
           {:source :complex-source
            :id (str "input-" token-id)
            :data-id idx
            :class "input-cell"
            :on-key-down (on-key-down id token-id)
            :on-mouse-down (on-mouse-down mouse-down? highlighted? selection token-id)
            :on-mouse-over (on-mouse-over mouse-down? highlighted? selection token-id)
            :on-mouse-up (on-mouse-up mouse-down?)}]])))))

(defn ann-types [hit-map]
  (let [thing   (->> (mapcat :anns (:hit hit-map))
       (map :ann)
       (map :key)
       (into (hash-set)))]
    thing))

(defn find-ann-by-key [by-key anns]
  (first (filter (fn [{{key :key} :ann}] (= by-key key)) anns)))

(defn annotation-rows
  "build component-fns [:tr] for each annotation in a given hit"
  [hit-map]
  (for [key (sort (map str (ann-types hit-map)))]
    [key
     (fn annotation-row-component [{:keys [hit]}]
       (into
        [:tr]
        (for [{:keys [id anns]} hit]
          (let [ann (find-ann-by-key key anns)]
            ^{:key (str key id)} [annotation-cell ann]))))]))

(defn queue-row
  "component for a queue row"
  [current-hit-id]
  (fn [{:keys [hit id]}]
    (into
     [:tr
      {:style {:background-color "#f5f5f5"
               :cursor "pointer"}
       :class "queue-row"
       :on-click #(reset! current-hit-id id)}]
     (for [{:keys [id] :as token} hit]
       ^{:key id} [token-cell token]))))


(defn get-target-hit-id
  [marked-hits current-ann-hit-id]
  (if-not current-ann-hit-id
    (:id (first marked-hits))
    current-ann-hit-id))

(defn spacer [& [space]]
  (fn []
    [:tr {:style {:height (str (or space 8) "px")}}]))

(defn rows
  "transforms hits into a vector of vectors [id component-fn];
  one hit may turn into multiple components (rows)"
  [{:keys [id] :as hit-map} marked-hits current-hit-id]
  (let [target-hit-id (get-target-hit-id @marked-hits @current-hit-id)]
    (if (= id target-hit-id)
      (concat
       [["hit"   hit-row]
        ["input" input-row]
        ["input-spacer" (spacer)]]
       (annotation-rows hit-map)
       [["ann-spacer" (spacer 16)]])
      [[(str "row-" id) (queue-row current-hit-id)]
       [(str "spacer-" id) (spacer)]])))

(defn annotation-component [marked-hits]
  (let [current-hit-id (reagent/atom nil)]
    (fn [marked-hits]
      [bs/table
       {:responsive true
        :id "table-annotation"}
       [:thead]
       [:tbody
        (doall
         (for [hit-map @marked-hits
               [id row-fn] (rows hit-map marked-hits current-hit-id)]
           ^{:key id} [row-fn hit-map]))]])))
