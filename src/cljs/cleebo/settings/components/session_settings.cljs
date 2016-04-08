(ns cleebo.settings.components.session-settings
  (:require [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [react-bootstrap.components :as bs]
            [cleebo.utils :refer [corpora]]
            [cleebo.query.components.query-field
             :refer [corpus-select context-select size-select]]))

(defn session-settings []
  (let [query-opts (re-frame/subscribe [:session :query-opts])]
    (fn []
      (let [has-query? false]
        [:div.container-fluid
         [:div.row.align-left
          [bs/label {:style {:font-size "14px" :line-height "2.5em"}} "Query options"]]
         [:div.row [:hr]]
         [:div.row
          [bs/button-toolbar
           {:class "text-center"}
           [corpus-select query-opts :corpora corpora]
           [context-select query-opts :has-query? false]
           [size-select query-opts :has-query? false]]]]))))
