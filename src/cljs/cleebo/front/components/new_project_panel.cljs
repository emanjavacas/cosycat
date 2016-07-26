(ns cleebo.front.components.new-project-panel
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [react-bootstrap.components :as bs]
            [schema.core :as s]
            [cleebo.schemas.project-schemas :refer [project-users-schema]]
            [cleebo.utils :refer [by-id parse-time human-time]]
            [cleebo.roles :refer [project-user-roles]]
            [cleebo.components :refer [user-selection-component css-transition-group]]
            [cleebo.front.components.include-box :refer [include-box-component]]
            [cleebo.app-utils :refer [invalid-project-name]]
            [goog.string :as gstr]
            [taoensso.timbre :as timbre]))

(def name-input-id :new-project-name-input)
(def desc-input-id :new-project-desc-input)

(defn label-component [title]
  (fn [title]
    [:div.text-muted.pull-right
     {:style {:padding-right "15px" :padding-top "5px"}}
     [:label title]]))

(defn spacer []
  [:div.row {:style {:height "35px"}}])

(defn on-input-change [has-error-atom component-id]
  (fn []
    (when @has-error-atom
      (re-frame/dispatch [:drop-error component-id]))))

(defn error-label [has-error-atom]
  (fn [has-error-atom]
    (if @has-error-atom
      [:span.help-block {:style {:color "red"}} (:msg @has-error-atom)]
      [:span.help-block])))

(defn name-input-component [name-input-error]
  (fn [name-input-error]
    [:div.row
     {:style {:padding  "0 15px 0 15px"}}
     [:div.input-group
      {:class (when @name-input-error "has-error")}
      [:span.input-group-addon "@"]
      [:input.form-control
       {:type "text"
        :id "name-input"
        :placeholder "Insert a beautiful name"
        :on-change (on-input-change name-input-error name-input-id)}]]
     [error-label name-input-error]
     [label-component "Project Name"]]))

(defn desc-input-component [desc-input-error]
  (let [chars (atom 0)]
    (fn [desc-input-error]
      [:div.row
       {:style {:padding "0 15px 0 15px"}}
       [:div.input-group
        {:class (when @desc-input-error "has-error")
         :style {:width "100%"}}
        [:textarea.form-control
         {:id "desc-input"
          :placeholder "Write a nice description about your project. Seriously."
          :rows "5"
          :on-key-press #(reset! chars (count (by-id "desc-input")))
          :on-change (on-input-change desc-input-error desc-input-id)
          :style {:resize "vertical"}}]]
       [error-label desc-input-error]
       [label-component (str "Add a Description (" @chars " characters left)")]])))

(defn move-cursor [dir els]
  (fn [idx]
    (let [[f top] (case dir
                    :next [inc (count els)]
                    :prev [dec (count els)])]
      (mod (f idx) top))))

(defn get-nth-role [idx]
  (first (nth (seq project-user-roles) idx)))

(defn on-click-fn [dir current-idx-atom on-change]
  (fn [e]
    (.stopPropagation e)
    (let [new-idx (swap! current-idx-atom (move-cursor dir project-user-roles))]
      (on-change (get-nth-role new-idx)))))

(defn select-role-btn [on-change]
  (let [current-idx (reagent/atom 0)]
    (fn [on-change]
      (let [[role desc] (nth (seq project-user-roles) @current-idx)]
        [:div
         [:div.input-group
          [:span.input-group-btn
           [:button.btn.btn-default
            {:type "button"
             :on-click (on-click-fn :prev current-idx on-change)}
            [bs/glyphicon {:glyph "chevron-left"}]]]
          [bs/overlay-trigger
           {:overlay (reagent/as-component [bs/tooltip {:id "tooltip"} desc])
            :placement "bottom"}
           [:span.form-control.text-center [bs/label role]]]
          [:span.input-group-btn
           [:button.btn.btn-default
            {:type "button"
             :on-click (on-click-fn :next current-idx on-change)}
            [bs/glyphicon {:glyph "chevron-right"}]]]]]))))

(defn is-selected? [selected-users username]
  (contains? selected-users username))

(s/defn get-selected-users [selected-users]
  :- (s/conditional empty? {} :else project-users-schema)
  (vec (vals @selected-users)))

(defn swap-selected [selected-users username]
  (if (is-selected? selected-users username)
    (dissoc selected-users username)
    (assoc selected-users username {:username username :role (first project-user-roles)})))

(def unselected-style
  {:-webkit-filter "grayscale(100%)"  :filter "grayscale(100%)" :opacity "0.6"})

(defn on-new-user-role [selected-users username]
  (fn [role]
    (timbre/debug
     (swap! selected-users assoc-in [username :role] role))))

(defn user-profile [user selected-users]
  (fn [{:keys [avatar username firstname lastname email created last-active]} selected-users]
    (let [selected? (is-selected? @selected-users username)]
      [:div.well.well-sm
       {:class (if-not selected? "selected")
        :style (merge {:cursor "pointer"} (when-not selected? unselected-style))
        :onClick #(swap! selected-users swap-selected username)}
       [:div.container-fluid
        [:div.row
         [:div.col-sm-6.col-md-4
          [:h4 [:img.img-rounded.img-responsive {:src (:href avatar)}]]]
         [:div.col-sm-6.col-md-8
          [:h4 username [:br] [:span [:small [:cite (str firstname " " lastname)]]]]]]
        [:div.row {:style {:padding "0 15px"}}
         [bs/table
          [:tbody
           [:tr [:td [bs/glyphicon {:glyph "envelope"}]] [:td email]]
           [:tr [:td [:span (str "Created:")]] [:td (parse-time created)]]
           [:tr [:td [:span (str "Last active:") ]] [:td (human-time last-active)]]]]]
        (when selected?
          [:div.row {:style {:padding "0 15px"}}
           [select-role-btn (on-new-user-role selected-users username)]])]])))

(defn users-input-component [selected-users]
  (let [users (re-frame/subscribe [:session :users])]
    (fn [selected-users]
      (timbre/debug @users)
      (when-not (empty? @users)
        [:div (doall (for [row (partition-all 3 @users)
                           {:keys [username] :as user} row]
                       (do (timbre/debug "USER" user)
                           ^{:key username}
                           [:div.col-lg-4 [user-profile user selected-users]])))]))))

(defn new-project-form [selected-users {:keys [name-input-error desc-input-error]}]
  (fn [selected-users {:keys [name-input-error desc-input-error]}]
    [bs/well
     [:div.container-fluid
      [name-input-component name-input-error]
      [spacer]
      [desc-input-component desc-input-error]
      [spacer]
      [users-input-component selected-users]]]))

(defn validate-project-input
  [{:keys [name description users] :as project} user-projects]
  (timbre/debug project)
  (cond
    (contains? (apply hash-set (map :name user-projects)) name)
    [name-input-id "Project already exists!"]
    (gstr/endsWith name "-playground")
    [name-input-id "Project name cannot end with '-playground'"]
    (invalid-project-name name)
    [name-input-id "Project name cannot match [ ^\\W+]"]
    (< (count description) 50)
    [desc-input-id "Type at least 50 characters... please!"]
    :else false))

(defn submit-project [{:keys [name description users user-projects]}]
  (let [project {:name name :description description :users users}]
    (if-let [[component-id error-msg] (validate-project-input project @user-projects)]
      (re-frame/dispatch [:register-error component-id {:msg error-msg}])
      (re-frame/dispatch [:new-project project]))))

(defn on-new-project [open? selected-users user-projects & {:keys [input-error-atoms]}]
  (fn []
    (if-not @open?
      (reset! open? true)
      (let [name (by-id "name-input")
            desc (by-id "desc-input")]
        (when-not (some identity (map deref input-error-atoms)) 
          (submit-project
           {:name name
            :description desc
            :users (get-selected-users selected-users)
            :user-projects user-projects}))))))

(defn project-btn [open? selected-users user-projects name-input-error desc-input-error]
  (fn [open? selected-users user-projects name-input-error desc-input-error]
    [bs/button-toolbar
     {:class "pull-right"}
     [bs/button
      {:onClick
       (on-new-project
        open? selected-users user-projects
        :input-error-atoms [name-input-error desc-input-error])
       :bsStyle (if-not @open? "info" "success")}
      (if-not @open? "New project" "Submit project")]
     (when @open?
       [bs/button
        {:onClick #(reset! open? false)
         :bsStyle "success"}
        "Close"])]))

(defn new-project-btn []
  (let [open? (reagent/atom false)
        users (re-frame/subscribe [:session :users])
        selected-users (reagent/atom {})
        name-input-error (re-frame/subscribe [:component-has-error? name-input-id])
        desc-input-error (re-frame/subscribe [:component-has-error? desc-input-id])
        user-projects (re-frame/subscribe [:session :user-info :projects])]
    (fn []
      [:div
       [css-transition-group
        {:transition-name "notification"
         :transition-enter-timeout 0
         :transition-leave-timeout 0}
        (when @open?
          [:div
           [:h2#new-project {:style {:padding "50px 0 30px 0"}} "New Project"]
           [new-project-form selected-users
            {:name-input-error name-input-error
             :desc-input-error desc-input-error}]])]
       [project-btn open? selected-users user-projects name-input-error desc-input-error]])))
