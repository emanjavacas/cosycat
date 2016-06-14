(ns cleebo.query.page
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [cleebo.utils :refer [format]]
            [cleebo.query.components.highlight-error :refer [highlight-error]]
            [cleebo.query.components.query-field :refer [query-field]]
            [cleebo.query.components.results-table :refer [results-table]]
            [cleebo.query.components.results-toolbar :refer [results-toolbar]]
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

(defn has-error [status]
  (= status :error))

(defn has-query-error [status]
  (= status :query-str-error))

(defn no-results [query-str query-size]
  (and (not (= "" query-str)) (zero? query-size)))

(defn has-results [query-size]
  (not (zero? query-size)))

(defn has-marked-hits [marked-hits]
  (not (zero? (count marked-hits))))

(defn results-frame []
  (let [status (re-frame/subscribe [:session :query-results :status])
        query-size (re-frame/subscribe [:session :query-results :query-size])
        query-str (re-frame/subscribe [:session :query-results :query-str])
        throbbing? (re-frame/subscribe [:throbbing? :results-frame])]
    (fn []
      (let [{:keys [status status-content]} @status]
        (cond
          @throbbing?                         [throbbing-panel]
          (has-error status)                  [internal-error-panel status-content]
          (has-query-error status)            [query-error-panel status-content]
          (no-results @query-str @query-size) [no-results-panel query-str]
          (has-results @query-size)           [results-table])))))

(defn label-closed-header [label]
  (fn [] [:div label]))

(defn results-closed-header []
  (let [query-size (re-frame/subscribe [:session :query-results :query-size])]
    (fn []
      [:div (str "Query results (Displaying " @query-size " hits)")])))

(defn annotation-closed-header []
  (let [marked-hits (re-frame/subscribe [:marked-hits {:has-marked? false}])]
    (fn []
      [:div (str "Annotation panel (" (count @marked-hits) " selected hits)")])))

(defn query-panel []
  (let [query-size (re-frame/subscribe [:session :query-results :query-size])
        marked-hits (re-frame/subscribe [:marked-hits {:has-marked? false}])]
    (fn []
      [:div.container
       {:style {:width "100%" :padding "0px 10px 0px 10px"}}
       [:div.row [minimize-panel
                  {:child query-field
                   :open-header (label-closed-header "Query field")
                   :closed-header (label-closed-header "Query field")}]]
       (when (has-results @query-size)
         [:div.row [minimize-panel
                    {:child results-frame
                     :open-header results-toolbar
                     :closed-header results-closed-header}]])
       (when (has-marked-hits @marked-hits)
         [:div.row [minimize-panel
                    {:child annotation-panel
                     :closed-header annotation-closed-header
                     :init false}]])
       [snippet-modal]])))
