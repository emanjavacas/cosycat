(ns cosycat.settings.components.session-settings
  (:require [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [react-bootstrap.components :as bs]
            [cosycat.utils :refer [nbsp ->map]]
            [cosycat.components :refer [dropdown-select]]
            [cosycat.query.components.query-toolbar
             :refer [corpus-select context-select size-select]]
            [cosycat.settings.components.shared-components
             :refer [row-component]]))

(def help-map
  {:context "This controls the length of the context window (in words) around the hit"
   :size "This let you control the number of hits to be shown per page"
   :snippet-size "Snippet size let you control the amount of words to be retrieve around the match
  when viewing the match in text-mode"
   :snippet-delta "This let you control how many extra words will be retrieved when fetching more
  snippet text"})

(defn on-mouse-over [target text-atom]
  (fn [e] (reset! text-atom (get help-map target))))

(defn on-mouse-out [text-atom]
  (fn [e] (reset! text-atom "")))

(defn query-opts-controller []
  (let [context (re-frame/subscribe [:settings :query :query-opts :context])
        page-size (re-frame/subscribe [:settings :query :query-opts :page-size])
        corpora (re-frame/subscribe [:corpora])
        query-opts-help (reagent/atom "")]
    (fn []
      [row-component
       :label "Query Options"
       :controllers
       [bs/button-toolbar
        {:class "text-center"}
        [context-select context
         :on-mouse-over (on-mouse-over :context query-opts-help)
         :on-mouse-out (on-mouse-out query-opts-help)          
         :has-query? false]
        [size-select page-size
         :on-mouse-over (on-mouse-over :size query-opts-help)
         :on-mouse-out (on-mouse-out query-opts-help)          
         :has-query? false]]
       :help-text query-opts-help])))

(defn snippet-controller []
  (let [snippet-size (re-frame/subscribe [:settings :query :snippet-opts :snippet-size])
        snippet-delta (re-frame/subscribe [:settings :query :snippet-opts :snippet-delta])
        snippet-size-help (reagent/atom "")]
    (fn []
      [row-component
       :label "Text Snippets"
       :controllers
       [bs/button-toolbar
        {:class "text-center"}
        [dropdown-select
         {:label "Snippet size: "
          :header "Select a snippet size"
          :options (map #(->map % %) [5 10 15 25 35 50 75 100 150])
          :model @snippet-size
          :select-fn (fn [choice]
                       (re-frame/dispatch
                        [:set-settings
                         [:query :snippet-opts :snippet-size] choice]))
          :on-mouse-over (on-mouse-over :snippet-size snippet-size-help)
          :on-mouse-out (on-mouse-out snippet-size-help)}]
        [dropdown-select
         {:label "Extra snippet text: "
          :header "Select number of words"
          :options (map #(->map % %) [5 10 15 25 35])
          :model @snippet-delta
          :select-fn (fn [choice]
                       (re-frame/dispatch
                        [:set-settings
                         [:query :snippet-opts :snippet-delta] choice]))
          :on-mouse-over (on-mouse-over :snippet-delta snippet-size-help)
          :on-mouse-out (on-mouse-out snippet-size-help)}]]
       :help-text snippet-size-help])))

(defn session-settings []
  [:div.container-fluid
   [query-opts-controller]
   [snippet-controller]])
