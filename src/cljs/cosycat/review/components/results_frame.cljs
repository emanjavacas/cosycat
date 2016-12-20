(ns cosycat.review.components.results-frame
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [react-bootstrap.components :as bs]
            [cosycat.components :refer [error-panel throbbing-panel]]
            [cosycat.app-utils :refer [parse-hit-id]]
            [cosycat.snippet :refer [snippet-modal]]
            [cosycat.annotation.components.annotation-component
             :refer [annotation-component]]))

(defn highlight-fn [{{:keys [anns]} :meta}]
  (fn [{id :_id}]
    (contains? anns id)))

(defn hit-row [hit-id]
  (let [hit-map (re-frame/subscribe [:project-session :review :results :results-by-id hit-id])
        color-map (re-frame/subscribe [:project-users-colors])]
    (fn [hit-id]
      [:div.row
       (if (get-in @hit-map [:meta :throbbing?])
         "loading..."
         [annotation-component @hit-map color-map
          :db-path :review
          :corpus (get-in @hit-map [:meta :corpus])
          :editable? true
          :highlight-fn (highlight-fn @hit-map)
          :show-match? false
          :show-hit-id? true])])))

(defn sort-by-doc [hit-ids]
  (sort-by #(let [{:keys [hit-start doc-id]} (parse-hit-id %)] [doc-id hit-start]) hit-ids))

(defn results-frame []
  (let [results (re-frame/subscribe [:project-session :review :results :results-by-id])
        throbbing? (re-frame/subscribe [:throbbing? :review-frame])]
    (fn []
      [:div.container-fluid
       (cond
         (empty? @results) [:div.row [error-panel {:status "Ooops! Found zero annotations"}]]
         @throbbing?       [:div.row [throbbing-panel :throbber :horizontal-loader]]
         :else             [:div.row (doall (for [hit-id (sort-by-doc (keys @results))]
                                              ^{:key (str "review-" hit-id)} [hit-row hit-id]))])
       [snippet-modal :review]])))
