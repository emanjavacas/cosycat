(ns cleebo.annotation.components.input-row
  (:require [reagent.core :as reagent]
            [cleebo.utils :refer [parse-annotation ->int]]
            [cleebo.backend.handlers.annotations :refer
             [dispatch-annotation dispatch-span-annotation ]]
            [cleebo.autocomplete :refer [autocomplete-jq]]
            [goog.dom.dataset :as gdataset]
            [goog.dom.classes :as gclass]))

(defn on-key-down [id token-id]
  (fn [pressed]
    (if (= 13 (.-keyCode pressed))
      (if-let [[k v] (parse-annotation (.. pressed -target -value))]
        (do
          (dispatch-annotation k v (->int id) (->int token-id))
          (set! (.-value (.-target pressed)) ""))))))

(defn on-mouse-down [mouse-down? highlighted? selection id]
  (fn [event]
    (let [e (aget event "target")]
;      (if @mouse-down? (.preventDefault event))
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
       [:tr]
       (for [[idx token] (map-indexed vector hit)
             :let [token-id (:id token)]]
         ^{:key (str "input-" id "-" token-id)}
         [:td.row-shadow
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
