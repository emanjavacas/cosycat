(ns cleebo.query.components.query-toolbar
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [clojure.string :as str]
            [react-bootstrap.components :as bs]
            [cleebo.utils :refer [->map by-id]]
            [cleebo.query-parser :refer [missing-quotes]]
            [cleebo.components :refer [dropdown-select]]
            [taoensso.timbre :as timbre]))

(defn on-select [query-opt & {:keys [has-query?]}]
  (fn [v]
    (re-frame/dispatch [:set-session [:query-opts query-opt] v])
    (when @has-query?
      (re-frame/dispatch [:query-refresh :results-frame]))))

(defn corpus-select [query-opts & {:keys [corpora] :as args}]
  (fn [query-opts & {:keys [corpora]}]
    (let [{:keys [corpus]} @query-opts
          args (dissoc args :corpora)]
      [dropdown-select
       (merge {:label "corpus: "
               :header "Select a corpus"
               :options (mapv #(->map % %) corpora)
               :model corpus
               :select-fn #(re-frame/dispatch [:set-session [:query-opts :corpus] %])}
              args)])))

(defn context-select [query-opts & {:keys [has-query?] :as args}]
  (fn [query-opts & {:keys [has-query?]}]
    (let [{:keys [context]} @query-opts
          args (dissoc args :has-query?)]
      [dropdown-select
       (merge {:label "window: "
               :header "Select window size"
               :options (map #(->map % %) (range 1 10))
               :model context
               :select-fn (on-select :context :has-query? has-query?)}
              args)])))

(defn size-select [query-opts & {:keys [has-query?] :as args}]
  (fn [query-opts & {:keys [has-query?] :as args}]
    (let [{:keys [size]} @query-opts
          args (dissoc args :has-query?)]
      [dropdown-select
       (merge 
        {:label "size: "
         :header "Select page size"
         :options (map #(->map % %) [5 10 15 25 35 55 85 125])
         :model size
         :select-fn (on-select :size :has-query? has-query?)}
        args)])))

(defn query-opts-menu []
  (let [query-opts (re-frame/subscribe [:session :query-opts])
        has-query? (re-frame/subscribe [:has-query?])
        corpora (re-frame/subscribe [:session :corpora])]
    (fn []
      [bs/button-toolbar
       [corpus-select query-opts :corpora @corpora]
       [context-select query-opts :has-query? has-query?]
       [size-select query-opts :has-query? has-query?]])))

(defn empty-before [s n]
  (count (filter #(= % " ")  (subs s n))))

(defn normalize-str [s]
  (str/replace s #"[ ]+" " "))

(defn trigger-query [query-str]
  (let [{status :status at :at} (missing-quotes query-str)
        at (+ at (empty-before query-str at))]
    (case status
      :mismatch (re-frame/dispatch
                 [:set-session [:query-results :status]
                  {:status :query-str-error
                   :status-content {:query-str query-str :at at}}])
      :finished (re-frame/dispatch [:query query-str :results-frame]))))

(defn on-key-press [k]
  (let [query-str (normalize-str (by-id "query-str"))]
    (when (and (not (zero? (count query-str))) (= (.-charCode k) 13))
      (trigger-query query-str))))

(defn on-click-search []
  (let [query-str (normalize-str (by-id "query-str"))]
    (when-not (zero? (count query-str))
      (trigger-query query-str))))

(defn query-toolbar []
  (let [query-str (re-frame/subscribe [:session :query-results :query-str])
        query-str-atom (reagent/atom @query-str)]
    (fn []
      [:div.row
       [:div.col-lg-5.col-sm-7
        [query-opts-menu]]
       [:div.col-lg-7.col-sm-5
        [:div.input-group
         [:input#query-str.form-control.form-control-no-border
          {:style {:width "100%"}
           :type "text"
           :name "query"
           :value @query-str-atom
           :placeholder "Example: [pos='.*\\.']" ;remove?
           :autoCorrect "false"
           :autoCapitalize "false"
           :autoComplete "false"
           :spellCheck "false"
           :on-change #(reset! query-str-atom (.. % -target -value))
           :on-key-press on-key-press}]
         [:span.input-group-addon
          {:on-click on-click-search
           :style {:cursor "pointer"}}
          [bs/glyphicon {:glyph "search"}]]]]]))) ;to do, add filter possibilities
