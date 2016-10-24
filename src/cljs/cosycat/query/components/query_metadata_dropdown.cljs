(ns cosycat.query.components.query-metadata-dropdown
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [clojure.string :as str]
            [react-bootstrap.components :as bs]
            [cosycat.utils :refer [->map by-id]]
            [cosycat.components :refer [dropdown-select]]
            [taoensso.timbre :as timbre]))

(defn query-metadata-dropdown []
  (let [queries (re-frame/subscribe [:project-queries])]
    (fn []
      [bs/dropdown
       {:id "dropdown"
        :pullRight true        
        :onSelect #(re-frame/dispatch [:set-query-metadata])}
       [bs/dropdown-toggle
        {:noCaret true}
        [bs/glyphicon {:glyph "pencil"}]]
       [bs/dropdown-menu
        (concat
         [^{:key "header"} [bs/menu-item {:header true} "Select Query"]
          ^{:key "divider"} [bs/menu-item {:divider true}]]
         (for [{id :id {query-str :query-str} :query-data :as query} @queries]
           ^{:key id} [bs/menu-item {:eventKey id} query-str]))]])))
