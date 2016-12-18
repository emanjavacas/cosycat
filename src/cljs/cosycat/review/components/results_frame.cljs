(ns cosycat.review.components.results-frame
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [react-bootstrap.components :as bs]
            [cosycat.app-utils :refer [parse-hit-id]]
            [cosycat.annotation.components.annotation-component
             :refer [annotation-component]]))

(defn hit-row [hit-id]
  (let [hit-map (re-frame/subscribe [:project-session :review :results :results-by-id hit-id])
        color-map (re-frame/subscribe [:project-users-colors])]
    (fn [hit-id]
      [:div.row
       (if (get-in @hit-map [:meta :throbbing?])
         "loading..."
         [annotation-component @hit-map color-map
          :editable? true
          :show-match? false
          :show-hit-id? false])])))

(defn sort-by-doc [hit-ids]
  (sort-by #(let [{:keys [hit-start doc-id]} (parse-hit-id %)] [doc-id hit-start]) hit-ids))

(defn results-frame []
  (let [path-to-subs [:project-session :review :results :results-summary :grouped-data]
        results (re-frame/subscribe path-to-subs)]
    (fn []
      [:div.container-fluid
       (doall (for [hit-id (sort-by-doc (keys @results))]
                ^{:key (str "review-" hit-id)} [hit-row hit-id]))])))

