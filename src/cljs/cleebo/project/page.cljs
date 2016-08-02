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
                   [:div.col-md-12
                    {:class (str "col-lg-" (int (ceil (/ 12 users-per-row))))}
                    [:div.well [project-user user]]]))]))

(defn key-val-span [key val]
  (fn [key val]
    [:span {:style {:font-size "18px"}}
     (str key ": ")
     [:h4.text-muted {:style {:display "inline-block"}}
      val]]))

(defn trigger-remove-project [project-name project-name-atom]
  (fn [event]
    (when (and (= 13 (.-charCode event)) (= project-name @project-name-atom))
      (.log js/console project-name))))

(defn compute-feedback [project-name project-name-atom]
  (.log js/console (= project-name @project-name-atom) project-name @project-name-atom)
  (cond (empty? @project-name-atom) ""
        (not= @project-name-atom project-name) "has-error"
        :else "has-success"))

(defn delete-project-modal [project-name delete-project-modal-show]
  (let [project-name-input-show (reagent/atom false)
        project-name-atom (reagent/atom "")]
    (fn [project-name delete-project-modal-show]
      [bs/modal
       {:show @delete-project-modal-show
        :onHide #(swap! delete-project-modal-show not)}
       [bs/modal-header
        {:closeButton true}
        [bs/modal-title
         {:style {:font-size "18px"}}
         "Do you really want to delete this project?"]]
       [bs/modal-body
        [:div.container-fluid
         (when @project-name-input-show
           [:div.row
            [:div.form-group
             {:class (compute-feedback project-name project-name-atom)}
             [:input.form-control
              {:value @project-name-atom
               :type "text"
               :on-key-press (trigger-remove-project project-name project-name-atom)
               :on-change #(reset! project-name-atom (.. % -target -value))}]]
            [:hr]])
         [:div.row
          [:div.text-center
           [bs/button-group
            [bs/button
             {:bsStyle "primary"
              :onClick #(swap! project-name-input-show not)}
             "Yes"]
            [bs/button
             {:onClick #(swap! delete-project-modal-show not)}
             "No"]]]]]]])))

(defn project-header [name]
  (let [delete-project-modal-show (reagent/atom false)]
    (fn [name]
      [:div.text [:h2 name]
       [:div.text.pull-right
        [bs/overlay-trigger
         {:overlay (reagent/as-component [bs/tooltip {:id "tooltip"} "Remove project"])
          :placement "right"}
         [bs/button
          {:bsSize "small"
           :onClick #(swap! delete-project-modal-show not)}
          [bs/glyphicon {:glyph "remove-sign"}]]]]
       [delete-project-modal name delete-project-modal-show]])))

(defn project-description []
  (let [active-project (re-frame/subscribe [:active-project])]
    (fn []
      (let [{:keys [users created description name session]} @active-project]
        [:div.container-fluid         
         [:div.row
          [project-header name]
          [:hr]
          [:div [key-val-span "description" description]]
          [:div [key-val-span "created" (human-time created)]]
          [:div [key-val-span "creator" (-> (filter #(= "creator" (:role %)) users) first :username)]]]
         [:div-row {:style {:margin "20px"}}
          [:div.text
           {:style {:margin "0 -15px"}}
           [:h4 "Users working in " [:span.text-muted name]]]]
         [:div.row [project-users users]]]))))

(defn project-panel []
  [:div.container
   [project-description]])

