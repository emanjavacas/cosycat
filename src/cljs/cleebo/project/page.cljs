(ns cleebo.project.page
  (:require [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [react-bootstrap.components :as bs]
            [cleebo.components :refer [user-profile-component]]
            [cleebo.roles :refer [project-user-roles]]
            [cleebo.utils :refer [human-time]]
            [cleebo.app-utils :refer [ceil]]))

(def users-per-row 3)

(defn project-user [{:keys [username]}]
  (let [user (re-frame/subscribe [:user username])]
    (fn [{:keys [username]}]
      [user-profile-component @user project-user-roles])))

(defn project-users [users]
  (fn [users]
    [:div (doall (for [row (partition-all users-per-row users)
                       {:keys [username] :as user} row]
                   ^{:key username}
                   [:div.well
                    {:class (str "col-lg-" (int (ceil (/ 12 users-per-row))))}
                    [project-user user]]))]))

(defn key-val-span [key val]
  (fn [key val]
    [:span {:style {:font-size "18px"}}
     (str key ": ")
     [:h4.text-muted {:style {:display "inline-block"}}
      val]]))

(defn project-description []
  (let [active-project (re-frame/subscribe [:active-project])]
    (fn []
      (let [{:keys [users created description name session]} @active-project]
        [:div.container-fluid         
         [:div.row
          [:div.text
           [:h2 name]]
          [:div [key-val-span "description" description]]
          [:div [key-val-span "created" (human-time created)]]
          [:div [key-val-span "creator" (-> (filter #(= "creator" (:role %)) users) first :username)]]]
         [:div-row {:style {:margin "20px"}}
          [:div.text
           {:style {:margin "0 -15px"}}
           [:h3 "Users working in " [:span.text-muted name]]]]
         (when-not (empty? users)
           [:div.row [project-users users]])]))))

(defn project-panel []
  [:div.container
   [project-description]])

