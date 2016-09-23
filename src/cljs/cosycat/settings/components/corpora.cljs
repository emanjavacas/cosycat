(ns cosycat.settings.components.corpora
  (:require [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [react-bootstrap.components :as bs]
            [cosycat.components :refer [dropdown-select throbbing-panel]]
            [cosycat.settings.components.shared-components :refer [row-component]]
            [cosycat.query-backends.core :refer [ensure-corpus]]
            [cosycat.tree :refer [data-tree]]
            [taoensso.timbre :as timbre]))

(defn corpus-info [{:keys [corpus info] :as corpus-config}]
  (ensure-corpus corpus-config)
  (fn [{:keys [corpus info] :as corpus-config}]
    [:div.container-fluid
     [:div.row [bs/label {:style {:font-size "14px" :line-height "2.5em"}} corpus]]
     [:div.row [:br]]
     (if (nil? info)
       [:div.row [throbbing-panel]]
       [:div.row [data-tree info :init-open false]])
     [:div.row [:hr]]]))

(defn corpora-settings []
  (let [corpora (re-frame/subscribe [:corpora])]
    (fn []
      [:div.container-fluid
       (doall (for [{:keys [corpus] :as corpus-config} @corpora]
                ^{:key corpus} [:div.row [corpus-info corpus-config]]))])))
