(ns cleebo.annotation.components.input-row
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [cleebo.utils :refer [parse-annotation ->int]]
            [cleebo.backend.handlers.annotations :refer [dispatch-annotation]]
            [cleebo.autocomplete :refer [autocomplete-jq]]))

(defn on-key-down
  "[TODO: clean code please]"
  [hit-id token-id span-selection]
  (fn [pressed]
    (if (= 13 (.-keyCode pressed))
      (if-let [[k v] (parse-annotation (.. pressed -target -value))]
        (let [ann {:key k :value v}
              hit-id (->int hit-id)
              token-id (->int token-id)]
          (if (contains? @span-selection token-id)
            (let [from (apply min @span-selection)
                  to (apply max @span-selection)]
              (if (not= (- to from) (dec (count @span-selection)))
                (do (re-frame/dispatch
                     [:notify {:message "Invalid span annotation" :status :error}]))
                (do (dispatch-annotation ann hit-id from to)
                    (set! (.-value (.-target pressed)) "")
                    (reset! span-selection #{}))))
            (do (dispatch-annotation ann hit-id token-id)
                (set! (.-value (.-target pressed)) "")
                (reset! span-selection #{})))
          (do (set! (.-value (.-target pressed)) "")
              (reset! span-selection #{})))))))

(defn input-row
  "component for the input row"
  [& {{hit :hit id :id meta :meta} :hit-map span-selection :span-selection}]
  (fn [& {{hit :hit id :id meta :meta} :hit-map span-selection :span-selection}]
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
          :on-key-down (on-key-down id token-id span-selection)}]]))))
