>(ns cosycat.front.components.new-project-panel
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [react-bootstrap.components :as bs]
            [cosycat.components :refer [css-transition-group]]
            [cosycat.utils :refer [by-id]]
            [cosycat.app-utils :refer [invalid-project-name atom? deep-merge]]
            [cosycat.autosuggest :refer [suggest-users]]
            [goog.string :as gstr]
            [taoensso.timbre :as timbre]))

(def name-input-id :new-project-name-input)
(def desc-input-id :new-project-desc-input)
(def min-chars 25)

(defn label-component
  ([title]
   (label-component {} title))
  ([opts title]
   (fn [opts title]
     [:div.text-muted.pull-right
      opts
      [:label title]])))

(defn spacer [] [:div.row {:style {:height "35px"}}])

(defn validate-name-input
  [name user-projects]
  (cond
    (contains? (apply hash-set (map :name user-projects)) name) "Project already exists!"
    (invalid-project-name name) "Project name cannot match [ ^\\W+]"
    :else false))

(defn name-input-component []
  (let [status (reagent/atom nil)
        user-projects (re-frame/subscribe [:session :user-info :projects])]
    (fn []
      [:div.row
       {:style {:padding  "0 15px 0 15px"}}
       [:div.input-group
        {:class (when @status "has-error")}
        [:span.input-group-addon "@"]
        [:input.form-control
         {:type "text"
          :id "name-input"
          :placeholder "Insert a beautiful name"
          :on-change #(reset! status (validate-name-input (by-id "name-input") @user-projects))}]]
       [label-component
        (when @status {:style {:color "red"}})
        (str "Project Name" (when @status (str " (" @status ")")))]])))

(defn validate-desc-input
  [desc]
  (let [remaining (- min-chars (count desc))]
    (when (pos? remaining) (str remaining " characters left"))))

(defn desc-input-component []
  (let [status (reagent/atom nil)]
    (fn []
      [:div.row
       {:style {:padding "0 15px 0 15px"}}
       [:div.input-group
        {:class (when @status "has-error")
         :style {:width "100%"}}
        [:textarea.form-control
         {:id "desc-input"
          :placeholder "Write a nice description about your project. Seriously."
          :rows "5"
          :on-change #(reset! status (validate-desc-input (by-id "desc-input")))
          :style {:resize "vertical"}}]]
       [label-component
        (when @status {:style {:color "red"}})
        (str "Add a Description" (when @status (str " (" @status ")")))]])))

(defn new-project-panel [selected-users]
  (fn [selected-users]
    [:div.container-fluid
     {:style {:border "1px solid rgba(0, 0, 0, 0.05)" :padding "15px"}}
     [name-input-component]
     [spacer]
     [desc-input-component]]))

(defn submit-project [{:keys [name description users user-projects]}]
  (let [project {:name name :description description :users users}]
    (when (and (not (validate-name-input name user-projects))
               (not (validate-desc-input description)))
      (re-frame/dispatch [:new-project project]))))

(defn on-new-project [open? selected-users user-projects]
  (fn []
    (if-not @open?
      (reset! open? true)
      (let [name (by-id "name-input"), desc (by-id "desc-input")]
        (submit-project
         {:name name
          :description desc
          :users (vec (vals @selected-users))
          :user-projects @user-projects})))))

(defn new-project-btn [open? selected-users]
  (assert (map? @selected-users) "selected-users must be map atom")
  (let [user-projects (re-frame/subscribe [:session :user-info :projects])]
    (fn [open? selected-users]
      [bs/button-toolbar
       {:class "pull-right"}
       [bs/button
        {:onClick (on-new-project open? selected-users user-projects)}
        (if-not @open? "New project" "Submit project")]
       (when @open?
         [bs/button
          {:onClick #(reset! open? false)}
          "Close"])])))
