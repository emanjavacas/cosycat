(ns cleebo.query.components.query-field
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [clojure.string :as str]
            [react-bootstrap.components :as bs]
            [cleebo.utils :refer [->map corpora by-id]]
            [cleebo.query-parser :refer [missing-quotes]]
            [cleebo.components :refer [dropdown-select]]
            [cleebo.query.logic :as q]))

(defn query-opts-menu [query-opts]
  (fn [query-opts]
    (let [{:keys [corpus context size]} @query-opts]
      [bs/button-toolbar
       [dropdown-select
        {:label "corpus: "
         :header "Select a corpus"
         :options (mapv #(->map % %) corpora)
         :model corpus
         :select-fn #(re-frame/dispatch [:set-session [:query-opts :corpus] %])}]
       [dropdown-select
        {:label "window: "
         :header "Select window size"
         :options (map #(->map % %) (range 1 10))
         :model context
         :select-fn #(re-frame/dispatch [:set-session [:query-opts :context] %])}]
       [dropdown-select
        {:label "size: "
         :header "Select page size"
         :options (map #(->map % %) [5 10 15 25 35 55 85 125 190 290 435 655 985])
         :model size
         :select-fn #(re-frame/dispatch [:set-session [:query-opts :size] %])}]])))

(defn empty-before [s n]
  (count (filter #(= % " ")  (subs s n))))

(defn normalize-str [s]
  (str/replace s #"[ ]+" " "))

(defn query-logic [& {:keys [query-opts query-results]}]
  (fn [k]
    (let [query-str (normalize-str (by-id "query-str"))]
      (if (and (not (zero? (count query-str))) (= (.-charCode k) 13))
        (let [{status :status at :at} (missing-quotes query-str)
              at (+ at (empty-before query-str at))
              args-map (assoc @query-opts :query-str query-str)]
          (case status
            :mismatch (re-frame/dispatch
                       [:set-session [:query-results :status]
                        {:status :query-str-error
                         :status-content {:query-str query-str :at at}}])
            :finished (do (re-frame/dispatch [:start-throbbing :results-frame])
                          (q/query args-map))))))))

(defn query-field []
  (let [query-opts (re-frame/subscribe [:query-opts])]
    (fn []
      [:div.row
       [:div.col-lg-2
        [:h4 [:span.text-muted {:style {:line-height "15px"}} "Query Panel"]]]
       [:div.col-lg-10
        [:div.row
         [:div.col-lg-4
          [query-opts-menu query-opts]]
         [:div.col-lg-8
          [:div.input-group
           [:input#query-str.form-control
            {:style {:width "100%"}
             :type "text"
             :name "query"
             :placeholder "Example: [pos='.*\\.']" ;remove?
             :autoCorrect "false"
             :autoCapitalize "false"
             :autoComplete "false"
             :spellCheck "false"
             :on-key-press (query-logic :query-opts query-opts)}]
           [:span.input-group-addon
            [bs/glyphicon {:glyph "search"}]]]]]]])))
