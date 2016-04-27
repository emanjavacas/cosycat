(ns cleebo.annotation.components.input-row
  (:require [cljs.core.async :refer [>! put!]]
            [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [cleebo.utils :refer [parse-annotation ->int filter-dummy-tokens]]
            [cleebo.components :refer [prepend-cell dummy-cell]]
            [cleebo.autocomplete :refer [autocomplete-jq]]))

(defn valid-span-range [to from selection]
  (not= (- to from) (dec (count selection))))

(defn handle-span-dispatch [ann hit-id selection]
  (let [from (apply min selection)
        to (apply max selection)]
    (if (valid-span-range to from selection)
      (re-frame/dispatch [:notify {:message "Invalid span annotation" :status :error}])
      (re-frame/dispatch [:dispatch-annotation ann hit-id from to]))))

(defn on-key-down
  [hit-id token-id selection]
  (fn [pressed]
    (if (= 13 (.-keyCode pressed))
      (if-let [[k v] (parse-annotation (.. pressed -target -value))]
        (let [ann {:key k :value v}
              hit-id (->int hit-id)
              token-id (->int token-id)
              my-selection (get @selection hit-id)]
          (do (if (contains? my-selection token-id)
                (handle-span-dispatch ann hit-id my-selection)
                (re-frame/dispatch [:dispatch-annotation ann hit-id token-id]))
              (set! (.-value (.-target pressed)) "")
              (swap! selection assoc hit-id #{})))))))

(defn input-row
  "component for the input row"
  [{hit :hit id :id meta :meta} open-hits & args]
  (fn input-row
    [{hit :hit id :id meta :meta} open-hits & {:keys [selection]}]
    (into
     [:tr]
     (-> (for [[idx {token-id :id :as token}] (map-indexed vector (filter-dummy-tokens hit))]
           ^{:key (str "input-" id "-" token-id)}
           [:td.row-shadow
            {:style {:padding "0px"}}
            [autocomplete-jq
             {:source :complex-source
              :id (str "input-" token-id)
              :data-id idx
              :class "input-cell"
              :on-key-down (on-key-down id token-id selection)}]])
         (prepend-cell
          {:key (str "dummy" id)
           :child dummy-cell})))))
