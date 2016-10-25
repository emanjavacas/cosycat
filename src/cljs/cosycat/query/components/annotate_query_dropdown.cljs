(ns cosycat.query.components.annotate-query-dropdown
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [clojure.string :as str]
            [react-bootstrap.components :as bs]
            [cosycat.utils :refer [->map by-id nbsp]]
            [cosycat.components :refer [dropdown-select]]
            [taoensso.timbre :as timbre]))

(defn launch-query []
  (let [queries (re-frame/subscribe [:project-queries])]
    (fn []
      [bs/dropdown
       {:id "dropdown"
        :pullRight true
        :disabled (boolean (empty? @queries))
        :onSelect #(re-frame/dispatch [:launch-query %2])}
       [bs/dropdown-toggle
        {:noCaret true}
        [bs/glyphicon {:glyph "chevron-down"}]]
       [bs/dropdown-menu        
        (concat
         [^{:key "header"} [bs/menu-item {:header true} "Select Query"]
          ^{:key "divider"} [bs/menu-item {:divider true}]]
         (doall (for [{id :id {:keys [corpus query-str]} :query-data :as query} @queries]
                  ^{:key id}
                  [bs/menu-item {:eventKey id}
                   [:span [:strong query-str] [:span {:style {:margin-left "10px"}} corpus]]])))]])))

(defn on-click [has-query? active-query]
  (fn []
    (cond
      @active-query (re-frame/dispatch [:unset-active-query])      
      @has-query? (re-frame/dispatch [:query-new-metadata]))))

(defn annotate-query []
  (let [has-query? (re-frame/subscribe [:has-query?])
        active-query (re-frame/subscribe [:project-session :active-query])]
    (fn []
      [bs/overlay-trigger
       {:placement "top"
        :overlay (reagent/as-component
                  [bs/tooltip {:id "tooltip" :style {:visibility (when-not @has-query? "hidden")}}
                   (if @active-query "Unset active query" "Annotate query")])}
       [bs/button {:onClick (on-click has-query? active-query)
                   :bsStyle (if-not @active-query "default" "primary")}
        [bs/glyphicon {:glyph "pencil"}]]])))

(defn annotate-query-dropdown []
  (fn []
    [bs/button-group
     [annotate-query]
     [launch-query]]))
