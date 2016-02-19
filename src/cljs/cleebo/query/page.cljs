(ns cleebo.query.page
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [cleebo.query.components.highlight-error :refer [highlight-error]]
            [cleebo.query.components.toolbar :refer [toolbar]]
            [cleebo.query.components.query-field :refer [query-field]]
            [cleebo.query.components.results-table :refer [results-table]]
            [cleebo.components :refer [error-panel]]
            [taoensso.timbre :as timbre]))

(defn throbbing-panel [] [:div.text-center [:div.loader]])

(defn results-frame []
  (let [status (re-frame/subscribe [:session :query-results :status])
        query-size (re-frame/subscribe [:session :query-results :query-size])
        throbbing? (re-frame/subscribe [:throbbing? :results-frame])]
    (fn []
      (let [{:keys [status status-content]} @status]
        (cond
          @throbbing?                 [throbbing-panel]
          (= status :error)           [error-panel
                                       :status "Oops! something bad happened"
                                       :status-content [:div status-content]]
          (= 0 @query-size)           [error-panel
                                       :status "The query returned no matching results"]
          (= status :query-str-error) [error-panel
                                       :status (str "Query misquoted starting at position "
                                                    (inc (:at status-content)))
                                       :status-content (highlight-error status-content)]
          (not (nil? @query-size))    [results-table]
          :else                       [error-panel :status "No results to be shown... 
                                       Go do some research!"])))))

(defn query-panel []
  [:div.container
   {:style {:width "100%" :padding "0px 10px 0px 10px"}}
   [:div.row [query-field]]
   [:div.row [toolbar]]
   [:br]
   [:div.row [results-frame]]])
