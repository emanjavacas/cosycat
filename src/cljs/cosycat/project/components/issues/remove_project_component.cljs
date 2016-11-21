(ns cosycat.project.components.issues.remove-project-component
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [react-bootstrap.components :as bs]
            [cosycat.utils :refer [format]]
            [cosycat.project.components.issues.issue-thread-component
             :refer [issue-thread-component]]
            [taoensso.timbre :as timbre]))

(defn remove-message [{{:keys [agreed]} :data}]
  (let [[one & others] agreed]
    (if-not others
      (format "%s wants to remove the project." one)
      (format "%s and %s want to remove the project." (apply str (interpose ", " others)) one))))

(defn pending-message [{{:keys [agreed]} :data} project-users]
  (let [[one & others] (remove (apply hash-set agreed) project-users)]
    (if-not others
      (format "%s is pending to decide on this issue." one)
      (format "%s and %s are pending to decide on this issue."
              (apply (str (interpose ", others")) one)))))

(defn remove-project-component [issue]
  (let [users (re-frame/subscribe [:active-project :users])
        me (re-frame/subscribe [:me :username])]
    (fn [{{:keys [agreed]} :data :as issue}]
      (let [me-pending? (not (some #{@me} agreed))
            rem-msg (remove-message issue)
            pen-msg (pending-message issue (map :username @users))]
        [:div.container-fluid
         [:div.row [:p rem-msg] [:p pen-msg]]
         [:div.row {:style {:height "10px"}}]
         [:div.row [issue-thread-component issue]]]))))

