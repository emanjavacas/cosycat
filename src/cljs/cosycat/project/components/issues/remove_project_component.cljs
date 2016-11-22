(ns cosycat.project.components.issues.remove-project-component
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [react-bootstrap.components :as bs]
            [clojure.string :refer [capitalize]]
            [cosycat.utils :refer [format]]
            [cosycat.project.components.issues.issue-thread-component
             :refer [issue-thread-component]]
            [taoensso.timbre :as timbre]))

(defn remove-message [{{:keys [agreed]} :data issue-id :id :as issue} me]
  (let [[one & others] agreed
        one (if (= one me) "you" one)
        others (map #(if (= % me) "you" %) others)]
    (if (empty? others)
      (if (= one "you")
        [:span [:strong "You"] " want to remove the project."]
        [:span [:strong one] " wants to remove the project."])
      (let [sep (if (< 2 (count others)) ", " "")]
        [:span (doall (for [other (cons (capitalize (first others)) (rest others))]
                        ^{:key (str other "-" issue-id)}
                        [:span [:strong other]
                         sep]))
         " and "
         [:strong one]
         " want to remove the project."]))))

(defn you-pending [& {:keys [capitalize?] :or {capitalize? false}}]
  [:span {:style {:cursor "pointer"}}
   [:a {:onClick #(re-frame/dispatch [:open-modal :delete-project])}
    [:strong (if capitalize? "You" "you")]]])

(defn pending-message [{{:keys [agreed]} :data issue-id :id :as issue} project-users me]
  (let [[one & others] (remove (apply hash-set agreed) project-users)
        one (if (= one me) "you" one)
        others (map #(if (= % me) "you" %) others)]
    (if (empty? others)
      (if (= one "you")
        [:span [you-pending :capitalize? true] " are pending to decide on this issue."]
        [:span [:strong one] " is pending to decide on this issue."])
      (let [sep (if (< 2 (count others)) ", " "")]
        [:span (doall (for [other (cons (capitalize (first others)) (rest others))]
                        ^{:key (str other "-" issue-id)}
                        [:span (cond
                                 (= other "You") [you-pending :capitalize? true]
                                 (= other "you") [you-pending]
                                 :else [:strong other])
                         sep]))
         " and "
         (if (= one "you") [you-pending] [:strong one])
         " are pending to decide on this issue."]))))

(defn remove-project-component [issue]
  (let [users (re-frame/subscribe [:active-project :users])
        me (re-frame/subscribe [:me :username])]
    (fn [{{:keys [agreed]} :data :as issue}]
      [:div.container-fluid
       [:div.row
        [:p [remove-message issue @me]]
        [:p [pending-message issue (map :username @users) @me]]]
       [:div.row {:style {:height "10px"}}]
       [:div.row [issue-thread-component issue]]])))

