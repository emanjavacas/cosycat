(ns cosycat.settings.components.appearance
  (:require [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [react-bootstrap.components :as bs]
            [cosycat.app-utils :refer [dekeyword]]
            [cosycat.components :refer [dropdown-select]]
            [cosycat.settings.components.shared-components :refer [row-component]]
            [cosycat.backend.db :refer [default-settings]]
            [taoensso.timbre :as timbre]))

(def verbosity-help-map
  {:signup "Get notified when someone signs up for the app"
   :login "Get notified when someone logs in"
   :logout "Get notified when someone logs out"
   :remove-project "Get notified when a project is removed"
   :new-project-issue "Get notified when an issue is created"
   :update-project-issue "Get notified when an issue is updated"
   :close-project-issue "Get notified when an issue is closed"
   :add-project-user "Get notified when you are added to a new project"
   :new-project-user "Get notified when a user is added to one of your projects"
   :remove-project-user "Get notified when a user leaves one of your projects"
   :new-project-user-role "Get notified when a user role is updated in one of your projects"
   :new-query-metadata "Get notified when a new query annotation is added to one of your projects"
   :update-query-metadata "Get notified when a hit query annotation is updated"
   :drop-query-metadata "Get notified when a query annotation is removed"
   :new-user-avatar "Get notified when a user updates their avatar"
   :new-user-info "Get notified when a user updates their profile"})

(def help-map
  (-> {:avatar "Click to generate a new avatar with random seed"
       :delay "Set a time (in msec) to wait until notifications fade out"}
      (merge verbosity-help-map)))

(defn on-mouse-over [target text-atom] (fn [e] (reset! text-atom (get help-map target))))

(defn on-mouse-out [text-atom] (fn [e] (reset! text-atom "")))

(defn get-default [path] (get-in (default-settings) path))

(defn appearance-controller []
  (let [help-text (reagent/atom "")]
    (fn []
      [row-component
       :label "Avatar"
       :controllers [bs/button
                     {:onClick #(re-frame/dispatch [:regenerate-avatar])
                      :on-mouse-over (on-mouse-over :avatar help-text)}
                     "Get new avatar"]
       :help-text help-text])))

(defn on-click-delay [v] (re-frame/dispatch [:set-settings [:notifications :delay] v]))

(defn notification-delay-controller [notification-help]
  (let [delay (re-frame/subscribe [:settings :notifications :delay])]
    (fn [notification-help]
      [:div.btn-toolbar
       [:div.input-group
        {:style {:width "150px"}
         :on-mouse-over (on-mouse-over :delay notification-help)
         :on-mouse-out (on-mouse-out notification-help)}
        [:span.input-group-btn
         [:button.btn.btn-default
          {:type "button"
           :on-click #(on-click-delay (max 0 (- @delay 250)))}
          [bs/glyphicon {:glyph "minus"}]]]
        [:span.form-control.input-number @delay]
        [:span.input-group-btn
         [:button.btn.btn-default
          {:type "button"
           :on-click #(on-click-delay (+ @delay 250))}
          [bs/glyphicon {:glyph "plus"}]]]]
       [:button.btn.btn-default
        {:type "button"
         :on-click #(on-click-delay (get-default [:notifications :delay]))}
        "Set default"]])))

(defn format-verbosity-key [verbosity-key]
  (->> (-> verbosity-key
           dekeyword
           (clojure.string/split #"-"))
       (interpose " ")
       (apply str)))

(defn verbosity-controller [verbosity-key notification-verbosity-help]
  (let [current (re-frame/subscribe [:settings :notifications :verbosity verbosity-key])]
    (fn [verbosity-key notification-verbosity-help]
      [:label.checkbox.inline.highlightable
       {:on-mouse-over (on-mouse-over verbosity-key notification-verbosity-help)
        :on-mouse-out (on-mouse-out notification-verbosity-help)}
       [:input
        {:type "checkbox"
         :checked @current
         :on-change #(re-frame/dispatch
                      [:set-settings [:notifications :verbosity verbosity-key] (not @current)])}]
       [:strong (format-verbosity-key verbosity-key)]])))

(defn notification-controller []
  (let [notification-help (reagent/atom "")
        notification-verbosity-help (reagent/atom "")]
    (fn []
      [:div
       [row-component
        :label "Notification Delay"
        :controllers [notification-delay-controller notification-help]
        :help-text notification-help]
       [row-component
        :label "Notification verbosity"
        :controllers [:div.container
                      (doall
                       (for [verbosity-key (keys verbosity-help-map)]
                         ^{:key (str "verbosity-" (dekeyword verbosity-key))}
                         [verbosity-controller verbosity-key notification-verbosity-help]))]
        :help-text notification-verbosity-help]])))

(defn appearance-settings []
  [:div.container-fluid
   [appearance-controller]
   [notification-controller]])
