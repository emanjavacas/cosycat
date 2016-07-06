(ns cleebo.query.components.query-toolbar
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [clojure.string :as str]
            [react-bootstrap.components :as bs]
            [cleebo.utils :refer [->map by-id]]
            [cleebo.components :refer [dropdown-select]]
            [taoensso.timbre :as timbre]))

(defn on-select [query-opt & {:keys [has-query?]}]
  (fn [v]
    (re-frame/dispatch [:set-settings [:query :query-opts query-opt] v])
    (when @has-query?
      (re-frame/dispatch [:query-refresh :results-frame]))))

(defn corpus-select [corpus & {:keys [corpora] :as args}]
  (fn [corpus & {:keys [corpora]}]
    [dropdown-select
     (merge {:label "corpus: "
             :header "Select a corpus"
             :options (mapv #(->map % %) corpora)
             :model @corpus
             :select-fn #(re-frame/dispatch [:set-settings [:query :corpus] %])}
            (dissoc args :corpora))]))

(defn context-select [context & {:keys [has-query?] :as args}]
  (fn [context & {:keys [has-query?]}]
    [dropdown-select
     (merge {:label "window: "
             :header "Select window size"
             :options (map #(->map % %) (range 1 10))
             :model @context
             :select-fn (on-select :context :has-query? has-query?)}
            (dissoc args :has-query?))]))

(defn size-select [page-size & {:keys [has-query?] :as args}]
  (fn [page-size & {:keys [has-query?] :as args}]    
    [dropdown-select
     (merge 
      {:label "size: "
       :header "Select page size"
       :options (map #(->map % %) [2 5 7 10 15 25 35 55 85 125])
       :model @page-size
       :select-fn (on-select :page-size :has-query? has-query?)}
      (dissoc args :has-query?))]))

(defn query-opts-menu []
  (let [corpus (re-frame/subscribe [:settings :query :corpus])
        context (re-frame/subscribe [:settings :query :query-opts :context])
        page-size (re-frame/subscribe [:settings :query :query-opts :page-size])
        has-query? (re-frame/subscribe [:has-query?])
        corpora (re-frame/subscribe [:corpora])]
    (fn []
      [bs/button-toolbar
       [corpus-select corpus :corpora @corpora]
       [context-select context :has-query? has-query?]
       [size-select page-size :has-query? has-query?]])))

(defn normalize-str [s]
  (str/replace s #"[ ]+" " "))

(defn on-key-press [k]
  (let [query-str (normalize-str (by-id "query-str"))]
    (when (and (not (zero? (count query-str))) (= (.-charCode k) 13))
      (re-frame/dispatch [:query query-str]))))

(defn on-click-search []
  (let [query-str (normalize-str (by-id "query-str"))]
    (when-not (zero? (count query-str))
      (re-frame/dispatch [:query query-str]))))

(defn query-toolbar []
  (let [query-str (re-frame/subscribe [:project-session :query :results-summary :query-str])
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
