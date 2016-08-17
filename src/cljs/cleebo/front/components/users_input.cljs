(ns cleebo.front.components.users-input
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [react-bootstrap.components :as bs]
            [cleebo.components :refer [user-profile-component]]
            [cleebo.front.components.include-box :refer [include-box-component]]
            [cleebo.roles :refer [project-user-roles]]
            [cleebo.app-utils :refer [ceil]]
            [taoensso.timbre :as timbre]))

(defn swap-selected [selected-users username]
  (if (contains? selected-users username)
    (dissoc selected-users username)
    (assoc selected-users username {:username username :role (first project-user-roles)})))

(def unselected-style
  {:-webkit-filter "grayscale(100%)"  :filter "grayscale(100%)" :opacity "0.6"})

(defn on-new-user-role [selected-users username]
  (fn [role]
    (timbre/debug (swap! selected-users assoc-in [username :role] role))))

(defn user-profile [user selected-users]
  (fn [{:keys [username] :as user} selected-users]
    (let [selected? (contains? @selected-users username)]
      [:div.well.well-sm
       {:class (if-not selected? "selected")
        :style (merge {:cursor "pointer"} (when-not selected? unselected-style))
        :onClick #(swap! selected-users swap-selected username)}
       [user-profile-component user project-user-roles
        :on-change (on-new-user-role selected-users username)]])))

(defn users-input-component [selected-users & {:keys [users-per-row] :or {users-per-row 3}}]
  (let [users (re-frame/subscribe [:users :exclude-me true])]
    (fn [selected-users]
      (when-not (empty? @users)
        [:div (doall (for [row (partition-all users-per-row @users)
                           {:keys [username] :as user} row]
                       ^{:key username}
                       [:div
                        (let [col-num (int (ceil (/ 12 users-per-row)))]
                          {:class (str "col-lg-" col-num)})
                        [user-profile user selected-users]]))]))))
