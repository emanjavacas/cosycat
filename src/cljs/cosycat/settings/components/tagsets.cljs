(ns cosycat.settings.components.tagsets
  (:require-macros [reagent.ratom :refer [reaction]])
  (:require [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [react-bootstrap.components :as bs]
            [cosycat.components :refer [dropdown-select throbbing-panel]]
            [cosycat.settings.components.shared-components :refer [row-component]]
            [cosycat.query-backends.core :refer [ensure-corpus]]
            [cosycat.tree :refer [data-tree]]
            [taoensso.timbre :as timbre]))

(defn new-tagsets [model-tagsets selected-tagsets tagset flag]
  (cond (and (not flag) (not selected-tagsets)) (vec (remove #(= % tagset) model-tagsets))
        (not flag) (vec (remove #(= % tagset) selected-tagsets))
        :else (conj (vec selected-tagsets) tagset)))

(defn on-change [model-tagsets selected-tagsets name checked?]
  (fn [e]
    (re-frame/dispatch
     [:set-project-settings [:tagsets] (new-tagsets @model-tagsets @selected-tagsets name checked?)])))

(defn check-tagset [name]
  (let [selected-tagsets (re-frame/subscribe [:active-project :settings :tagsets])
        model-tagsets (re-frame/subscribe [:tagsets :name])]
    (fn [name]
      (let [checked? (if-not @selected-tagsets true (some #{name} @selected-tagsets))]
        [:div
         [:h2.label.label-default {:style {:font-size "14px" :line-height "2.5em"}} name]
         [:span {:style {:padding-left "10px"}}
          [:input {:type "checkbox"
                   :checked checked?
                   :on-change (on-change model-tagsets selected-tagsets name (not checked?))}]]]))))

(defn tagset-info [{:keys [name] :as tagset}]
  (fn [{:keys [name] :as tagset}]
    [:div.container-fluid
     [:div.row [check-tagset name]]
     [:div.row [:br]]
     [:div.row [data-tree tagset :init-open false]]
     [:div.row [:hr]]]))

(defn tagsets-settings []
  (let [tagsets (re-frame/subscribe [:tagsets])]
    (fn []
      [:div.container-fluid
       (doall (for [{:keys [name] :as tagset} @tagsets]
                ^{:key name} [:div.row [tagset-info tagset]]))])))

