(ns cleebo.query.page
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [cleebo.utils :refer [format]]
            [cleebo.query.components.highlight-error :refer [highlight-error]]
            [cleebo.query.components.toolbar :refer [toolbar]]
            [cleebo.query.components.results-table :refer [results-table]]
            [cleebo.query.components.snippet-modal :refer [snippet-modal]]
            [cleebo.annotation.components.annotation-panel :refer [annotation-panel]]
            [cleebo.components :refer [error-panel throbbing-panel minimize-panel]]
            [taoensso.timbre :as timbre]))

(defn internal-error-panel [status-content]
  (fn [status-content]
    [error-panel
     :status "Oops! something bad happened"
     :status-content [:div status-content]]))

(defn query-error-panel [status-content]
  (fn [status-content]
    [error-panel
     :status (str "Query misquoted starting at position " (inc (:at status-content)))
     :status-content (highlight-error status-content)]))

(defn no-results-panel [query-str]
  (fn [query-str]
    [error-panel :status (format "No matches found for query: %s" @query-str)]))

(defn do-research-panel []
  [error-panel :status "No hits to be shown... Go do some research!"])

(defn results-frame []
  (let [status (re-frame/subscribe [:session :query-results :status])
        query-size (re-frame/subscribe [:session :query-results :query-size])
        query-str (re-frame/subscribe [:session :query-results :query-str])
        throbbing? (re-frame/subscribe [:throbbing? :results-frame])
        has-error (fn [status] (= status :error))
        query-error (fn [status] (= status :query-str-error))
        no-results (fn [q-str q-size] (and (not (= "" q-str)) (zero? q-size)))
        has-results (fn [query-size] (not (zero? query-size)))]
    (fn []
      (let [{:keys [status status-content]} @status]
        (cond
          @throbbing?                         [throbbing-panel]
          (has-error status)                  [internal-error-panel status-content]
          (query-error status)                [query-error-panel status-content]
          (no-results @query-str @query-size) [no-results-panel query-str]
          (has-results @query-size)           [results-table]
          :else                               [do-research-panel])))))

(defn query-panel []
  (let [has-query? (re-frame/subscribe [:has-query?])]
    (fn []
      [:div.container
       {:style {:width "100%" :padding "0px 10px 0px 10px"}}
       [:div.row [minimize-panel {:child toolbar :init true}]]
       [:div.row [minimize-panel {:child results-frame}]]
       [:div.row [minimize-panel {:child annotation-panel}]]
       [snippet-modal]])))
