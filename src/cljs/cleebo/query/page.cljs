(ns cleebo.query.page
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [cleebo.query.components.highlight-error :refer [highlight-error]]
            [cleebo.query.components.toolbar :refer [toolbar]]
            [cleebo.query.components.query-field :refer [query-field]]
            [cleebo.query.components.results-table :refer [results-table]]
            [cleebo.query.components.snippet-modal :refer [snippet-modal]]
            [cleebo.components :refer [error-panel]]
            [taoensso.timbre :as timbre]))

(defn throbbing-panel [] [:div.text-center [:div.loader]])

(defn results-frame []
  (let [status (re-frame/subscribe [:session :query-results :status])
        query-size (re-frame/subscribe [:session :query-results :query-size])
        query-str (re-frame/subscribe [:session :query-results :query-str])
        throbbing? (re-frame/subscribe [:throbbing? :results-frame])
        has-error (fn [status] (= status :error))
        query-error (fn [status] (= status :query-str-error))
        empty-results (fn [q-str q-size] (and (not (= "" q-str)) (zero? q-size)))
        has-results (fn [query-size] (not (zero? query-size)))]
    (fn []
      (let [{:keys [status status-content]} @status]
        (cond
          @throbbing?          [throbbing-panel]
          (has-error status)   [error-panel
                                :status "Oops! something bad happened"
                                :status-content [:div status-content]]
          (query-error status) [error-panel
                                :status (str "Query misquoted starting at position "
                                             (inc (:at status-content)))
                                :status-content (highlight-error status-content)]
          (empty-results
           @query-str
           @query-size)        [error-panel
                                :status "The query returned no matching results"]
          (has-results
           @query-size)        [results-table]
          :else                [error-panel
                                :status "No results to be shown... Go do some research!"])))))

(defn query-panel []
  (let [query-str (re-frame/subscribe [:session :query-results :query-str])
        has-query? (re-frame/subscribe [:has-query?])]
    (fn []
      [:div.container
       {:style {:width "100%" :padding "0px 10px 0px 10px"}}
       [:div.row [query-field query-str]]
       (when @has-query? [:div.row [toolbar]])
       [:br]
       [:div.row [results-frame]]
       [snippet-modal]])))
 
