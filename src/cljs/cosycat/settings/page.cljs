(ns cosycat.settings.page
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [react-bootstrap.components :as bs]
            [cosycat.settings.components.query :refer [query-settings]]
            [cosycat.settings.components.appearance :refer [appearance-settings]]
            [cosycat.settings.components.corpora :refer [corpora-settings]]
            [cosycat.settings.components.tagsets :refer [tagsets-settings]]
            [taoensso.timbre :as timbre]))

(def nav-item-style {:style {:font-weight "bold"}})

(defn tabs [active-tab expanded?]
  (fn [active-tab expanded?]
    [bs/nav
     {:bsStyle "tabs"
      :active-key @active-tab
      :on-select #(reset! active-tab (keyword %))}
     [bs/nav-item {:event-key :query} [:span nav-item-style "Query Settings"]]
     [bs/nav-item {:event-key :appearance} [:span nav-item-style "Appearance Settings"]]
     [bs/nav-item {:event-key :corpora} [:span nav-item-style "Corpora"]]
     [bs/nav-item {:event-key :tagsets} [:span nav-item-style "Tagsets"]]
     [:span.pull-right
      {:style {:cursor "pointer"}
       :on-click #(swap! expanded? not)}
      [bs/glyphicon {:glyph (if @expanded? "resize-small" "resize-full")}]]]))

(defmulti tab-panel identity)
(defmethod tab-panel :query [] [query-settings])
(defmethod tab-panel :appearance [] [appearance-settings])
(defmethod tab-panel :corpora [] [corpora-settings])
(defmethod tab-panel :tagsets [] [tagsets-settings])

(defmulti get-update-map (fn [active-tab _] active-tab))

(defmethod get-update-map :query
  [_ settings]
  (let [{{:keys [query-opts snippet-opts]} :query} settings]
    {:query {:query-opts {:context (query-opts :context)
                          :page-size (query-opts :page-size)}
             :snippet-opts {:snippet-size (snippet-opts :snippet-size)
                            :snippet-delta (snippet-opts :snippet-delta)}}}))

(defmethod get-update-map :appearance
  [_ settings]
  {:notifications {:delay (get-in settings [:notifications :delay])}})

(defmethod get-update-map :tagsets
  [_ {:keys [tagsets] :as settings}]
  {:tagsets tagsets})

(defn display-setting-submit [active-tab]
  (case active-tab
    :appearance true
    :corpora false
    true))

(defn submit-settings-btns [active-tab settings]
  (let [active-project (re-frame/subscribe [:session :active-project])]
    (fn [active-tab settings]
      [bs/button-toolbar
       [bs/button
        {:onClick #(re-frame/dispatch [:submit-settings (get-update-map @active-tab @settings)])
         :class "pull-right"
         :bsStyle "info"}
        "Save globally"]
       (when @active-project
         [bs/button
          {:onClick
           #(re-frame/dispatch [:submit-project-settings (get-update-map @active-tab @settings)])
           :class "pull-right"
           :bsStyle "info"}
          "Save for project"])])))

(defn settings-panel []
  (let [active-tab (reagent/atom :query)
        expanded? (reagent/atom true)
        settings (re-frame/subscribe [:settings])]
    (fn []
      [:div
       {:class (if @expanded? "container-fluid" "container")}
       [bs/panel {:header (reagent/as-component [tabs active-tab expanded?])}
        [:div.container-fluid [tab-panel @active-tab]]
        (when (display-setting-submit @active-tab) [submit-settings-btns active-tab settings])]])))
