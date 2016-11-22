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

(defn new-tagsets [model-tagsets selected-tagsets tagset-name flag]
  (cond (and (not flag) (not selected-tagsets)) (vec (remove #(= % tagset-name) model-tagsets))
        (not flag) (vec (remove #(= % tagset-name) selected-tagsets))
        :else (conj (vec selected-tagsets) tagset-name)))

(defn on-change [model-tagsets selected-tagsets name checked?]
  (fn [e]
    (re-frame/dispatch
     [:set-project-settings [:tagsets]
      (new-tagsets @model-tagsets @selected-tagsets name checked?)])))

(defn check-tagset [tagset-name model-tagsets]
  (let [selected-tagsets (re-frame/subscribe [:active-project :settings :tagsets])]
    (fn [tagset-name]
      (let [checked? (if-not @selected-tagsets true (some #{tagset-name} @selected-tagsets))]
        [:div
         [:h2.label.label-default {:style {:font-size "14px" :line-height "2.5em"}} tagset-name]
         [:span {:style {:padding-left "10px"}}
          [:input
           {:type "checkbox"
            :checked checked?
            :on-change (on-change model-tagsets selected-tagsets tagset-name (not checked?))}]]]))))

(defn tagset-info [{:keys [name] :as tagset} model-tagsets]
  (fn [{:keys [name] :as tagset} model-tagsets]
    [:div.container-fluid
     [:div.row [check-tagset name model-tagsets]]
     [:div.row [:br]]
     [:div.row [data-tree tagset :init-open false]]
     [:div.row [:hr]]]))

(defn tagsets-settings []
  (let [model-tagsets (re-frame/subscribe [:tagsets])]
    (fn []
      [:div.container-fluid
       (doall (for [{:keys [name] :as tagset} @model-tagsets]
                ^{:key name} [:div.row [tagset-info tagset model-tagsets]]))])))

