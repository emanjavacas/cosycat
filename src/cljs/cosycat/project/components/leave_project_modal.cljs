(ns cosycat.project.components.leave-project-modal
  (:require [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [react-bootstrap.components :as bs]
            [cosycat.components :refer [compute-feedback]]
            [cosycat.utils :refer [human-time]]
            [taoensso.timbre :as timbre]))

(defn leave-project [project-name project-name-atom]
  (when (compute-feedback project-name project-name-atom)
    (do (re-frame/dispatch [:close-modal :leave-project])
        (re-frame/dispatch [:project-remove-user project-name]))))

(defn on-key-press [project-name project-name-atom]
  (fn [e]
    (when (= 13 (.-charCode e))
      (reset! project-name-atom "")
      (leave-project project-name project-name-atom))))

(defn project-name-input [project-name]
  (let [project-name-atom (reagent/atom "")]
    (fn [project-name]
      [:div.container-fluid
       [:div.row
        [:div.form-group
         {:class (compute-feedback project-name project-name-atom)}
         [:input.form-control
          {:value @project-name-atom
           :type "text"
           :on-key-press (on-key-press project-name project-name-atom)
           :on-change #(reset! project-name-atom (.. % -target -value))}]]]
       [:div.row
        [:div.text-center
         [:div.pull-right "Type in the name of the project you wish to leave"]]]
       [:div.row {:style {:height "10px"}}]
       [:div.row.pull-right
        [bs/button
         {:onClick #(leave-project project-name project-name-atom )}
         [bs/glyphicon {:glyph "hand-right"}]]]])))

(defn double-check-button [project-input-show?]
  (fn [project-input-show?]
    [:div.text-center
     [bs/button-group
      [bs/button
       {:bsStyle "primary"
        :onClick #(swap! project-input-show? not)}
       "Yes"]
      [bs/button
       {:onClick #(re-frame/dispatch [:close-modal :leave-project])}
       "No"]]]))

(defn leave-project-modal [project-name]
  (let [project-input-show? (reagent/atom false)
        show? (re-frame/subscribe [:modals :leave-project])]
    (fn [project-name]
      [bs/modal
       {:show @show?
        :onHide #(re-frame/dispatch [:close-modal :leave-project])}
       [bs/modal-header
        {:closeButton true}
        [bs/modal-title "Do you really want to leave?"]]
       [bs/modal-body
        [:div.container-fluid
         (if @project-input-show?
           [project-name-input project-name]
           [double-check-button project-input-show?])]]])))
