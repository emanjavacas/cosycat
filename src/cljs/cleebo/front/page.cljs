(ns cleebo.front.page
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [react-bootstrap.components :as bs]
            [cleebo.routes :refer [nav!]]
            [cleebo.utils :refer [by-id nbsp css-transition-group]]
            [cleebo.components :refer [user-thumb throbbing-panel]]
            [goog.string :as gstr]
            [taoensso.timbre :as timbre]))

(defn validate-project-input
  [{:keys [name description users] :as project}]
  true)

(defn submit-project [name desc users]
  (let [project {:name name :description desc :users users}]
    (validate-project-input project)
    (re-frame/dispatch [:new-project project])))

(defn user-selection-component [child-data]
  (fn [{:keys [username avatar last-active]}]
    [:div username
     [:span
      {:style {:padding-left "10px"}}
      [user-thumb username {:height "25px" :width "25px"}]]]))

(defn include-box [model selection child-component min-children]
  (fn [model selection child-component min-children]
    (let [children (concat @model (repeat (- min-children (count @model)) nil))]
      [bs/list-group
       (doall (for [[idx child-data] (map-indexed vector children)]
                (if child-data
                  ^{:key (str idx)}
                  [bs/list-group-item
                   {:onClick #(reset! selection #{child-data})
                    :style {:min-height "50px"}}
                   (reagent/as-component [:div.text-center [child-component child-data]])]
                  ^{:key (str idx)}
                  [bs/list-group-item
                   {:style {:min-height "50px"}}
                   (nbsp 10)])))])))

(defn left-right-click
  [target-model source-model source-selection]
  (fn [event]
    (.preventDefault event)
    (swap! target-model clojure.set/union @source-selection)
    (swap! source-model clojure.set/difference @source-selection)
    (reset! source-selection #{})))

(defn include-box-component [{:keys [model on-select]}]
  (let [model-left (reagent/atom #{})
        model-right (reagent/atom (apply hash-set model))
        selection-left (reagent/atom #{})
        selection-right (reagent/atom #{})
        _ (add-watch model-left :sel (fn [_ _ _ new-state] (on-select new-state)))]
    (fn [{:keys [model selection-atom]}]
      (let [nchildren (count @model-right)]
        [:div.container-fluid
         [:div.row
          [:div.col-lg-5
           [include-box model-left selection-left user-selection-component nchildren]
           [:span.text-muted.pull-right
            [bs/label "Selected users"]]]
          [:div.col-lg-2.text-center
           {:style {:margin-top (str (* 11 nchildren) "px")}} ;dirty fix
           [:div.row
            [bs/button
             {:onClick (left-right-click model-left model-right selection-right)}
             [bs/glyphicon {:glyph "chevron-left"}]]]
           [:div.row
            [bs/button
             {:onClick (left-right-click model-right model-left selection-left)}
             [bs/glyphicon {:glyph "chevron-right"}]]]]
          [:div.col-lg-5
           [include-box model-right selection-right user-selection-component nchildren]
           [:span.text-muted.pull-right
            [bs/label "Available users"]]]]]))))

(defn label-component [title]
  [:div.row
   [:div.text-muted.pull-right
    {:style {:padding-right "15px" :padding-top "15px"}}
    [:label title]]])

(defn spacer []
  [:div.row {:style {:height "35px"}}])

(defn new-project-form [selected-users]
  (let [users (re-frame/subscribe [:session :users])]
    (fn [selected-users]
      [bs/well
       [:div.container-fluid
        ;; project name
        [:div.row
         {:style {:padding  "0 15px 0 15px"}}
         [:div.input-group
          [:span.input-group-addon "@"]
          [:input.form-control
           {:type "text"
            :id "name-input"
            :placeholder "Insert a beautiful name"}]]]
        [:div.row
         {:style {:padding-right "25px"}}
         [label-component "Project Name"]]
        [spacer]
        ;; add description
        [:div.row
         {:style {:padding "0 15px 0 15px"}}
         [:textarea.form-control
          {:id "desc-input"
           :placeholder "Write a nice description about your project. Seriously."
           :rows "5"
           :style {:resize "vertical"}}]]
        [:div.row
         {:style {:padding-right "25px"}}
         [label-component "Add a Description"]]
        [spacer]
        ;; add users
        (when-not (empty? @users)
          [:div.row
           [include-box-component
            {:model @users
             :on-select #(reset! selected-users %)}]])
        (when-not (empty? @users)
          [:div.row
           {:style {:padding-right "25px"}}
           [label-component "Add Users"]])]])))

(defn new-project-btn [& {:keys [on-open on-hide]}]
  (let [open? (reagent/atom false)
        selected-users (reagent/atom #{})
        _ (add-watch open? :open (fn [_ _ _ new-state]
                               (if new-state
                                 (when on-open (on-open))
                                 (when on-hide (on-hide)))))] 
    (fn [& {:keys [on-open on-hide]}]
      [:div
       [css-transition-group
        {:transition-name "notification"}
        (when @open? [new-project-form selected-users])]
       [bs/button-toolbar
        {:class "pull-right"}
        [bs/button
         {:onClick #(if-not @open?
                      (reset! open? true)
                      (let [name (by-id "name-input")
                            desc (by-id "desc-input")]
                        (submit-project name desc (map :username @selected-users))))
          :bsStyle (if-not @open? "info" "success")}
         (if-not @open? "New project" "Submit project")]
        (when @open?
          [bs/button
           {:onClick #(reset! open? false)
            :bsStyle "success"}
           "Close"])]])))

(defn no-projects []
  [:div
   [:p "You don't have current projects. Start one right now."]
   [new-project-btn]])

(defn projects-panel [projects]
  (let [db-users (re-frame/subscribe [:session :users])
        new? (reagent/atom false)]
    (fn [projects]
      [:div.container-fluid
       (when-not @new?
         [:div.row
          [:div
           [bs/list-group
            (doall
             (for [{:keys [name creator description users updates]} @projects
                   :let [user (first (filter #(= creator (:username %)) @db-users))]]
               ^{:key (str name)}
               [bs/list-group-item
                {:onClick #(nav! (str "/project/" name))}
                (reagent/as-component
                 [:div.container-fluid
                  [:div.row
                   [:div.col-lg-8 [:p (gstr/capitalize name)]]]
                  [:div.row
                   [:div.col-lg-3 [:span.text-muted "Created by: "]]
                   [:div.col-lg-9 [user-selection-component (first @db-users)]]]
                  [:div.row
                   [:div.col-lg-3 [:span.text-muted "Project description: "]]
                   [:div.col-lg-9 description]]])]))]]])
       [:div.row [new-project-btn
                  :on-open #(reset! new? true)
                  :on-hide #(reset! new? false)]]])))

(defn my-throbbing-panel []
  [:div.container-fluid
   [:div.row.text-center
    {:style {:height "250px"}}
    [throbbing-panel :css-class "loader-ticks"]]
   [:div.row.text-center
    [:h2.text-muted "Loading Database"]]])

(defn front-panel []
  (let [projects (re-frame/subscribe [:session :user-info :projects])
        throbbing? (re-frame/subscribe [:throbbing? :front-panel])]
    (fn []
      [:div.container-fluid
       [:div.col-lg-1]
       [:div.col-lg-10
        [:div
         (if @throbbing?
           [my-throbbing-panel]
           [bs/jumbotron
            [:h2
             {:style {:padding-bottom "50px"}}
             "Projects"]
            (if (empty? @projects)
              [no-projects]
              [projects-panel projects])])]]
       [:div.col-lg-1]])))
