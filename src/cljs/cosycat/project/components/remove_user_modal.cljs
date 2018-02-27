(ns cosycat.project.components.remove-user-modal
  (:require [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [react-bootstrap.components :as bs]
            [taoensso.timbre :as timbre]))

(defn remove-user [project-name username]
  (re-frame/dispatch [:project-kick-user project-name username])
  (re-frame/dispatch [:close-modal :remove-user]))

(defn double-check-button [project-name username]
  (fn [project-name username]
    [:div.text-center
     [bs/button-group
      [bs/button
       {:bsStyle "primary"
        :onClick #(remove-user project-name username)}
       "Yes"]
      [bs/button
       {:onClick #(re-frame/dispatch [:close-modal :remove-user])}
       "No"]]]))

(defn remove-user-modal [project-name]
  (let [show? (re-frame/subscribe [:modals :remove-user])]
    (fn [project-name]
      (let [username (:username @show?)]
        [bs/modal
         {:show (boolean @show?)
          :onHide #(re-frame/dispatch [:close-modal :remove-user])}
         [bs/modal-header
          {:closeButton true}
          [bs/modal-title
           [:span "Do you really want to remove " [:strong username] " from " [:strong project-name] "?"]]]
         [bs/modal-body
          [:div.container-fluid [double-check-button project-name username]]]]))))

