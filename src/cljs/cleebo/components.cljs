(ns cleebo.components
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [cleebo.utils :refer [color-codes date-str->locale]]
            [cleebo.localstorage :as ls]
            [taoensso.timbre :as timbre]
            [goog.string :as gstr]
            [react-bootstrap.components :as bs]))

(def css-transition-group
  (reagent/adapt-react-class js/React.addons.CSSTransitionGroup))

(defn throbbing-panel [& {:keys [css-class] :or {css-class "loader"}}]
  [:div.text-center [:div {:class css-class}]])

(defn error-panel [& {:keys [status content]}]
  {:pre [(and status)]}
  [:div.container-fluid.text-center
   {:style {:padding "40px"}}
   [:div.row [:h3 [:span.text-muted status]]]
   [:div.row [:br]]
   [:div.row.text-center content]])

(defn dropdown-select [{:keys [label model options select-fn header] :as args}]
  (let [local-label (reagent/atom model)]
    (fn [{:keys [label model options select-fn header] :or {select-fn identity}}]
      (let [args (dissoc args :label :model :options :select-fn :header)]
        [bs/dropdown
         (merge
          {:id "my-dropdown"
           :onSelect (fn [e k] (reset! local-label k) (select-fn k))}
          args)
         [bs/button
          {:style {:pointer-events "none !important"}}
          [:span.text-muted label] @local-label]
         [bs/dropdown-toggle]
         [bs/dropdown-menu
          (concat
           [^{:key "header"} [bs/menu-item {:header true} header]
            ^{:key "divider"} [bs/menu-item {:divider true}]]
           (for [{:keys [key label]} options]
             ^{:key key} [bs/menu-item {:eventKey label} label]))]]))))

(defn status-icon [status]
  [:span.label.pull-left
   {:style {:margin-left "10px"
            :margin-top "0px"
            :font-size "20px"
            :color (color-codes status)}}
   [:i.zmdi
    {:style {:line-height "1.4em"
             :font-size "16px"}
     :class (case status
              :info  "zmdi-info"
              :ok    "zmdi-storage"
              :error "zmdi-alert-circle")}]])

(defn right-href [href]
  (if (.startsWith href "public")
    (second (gstr/splitLimit href "/" 1))
    href))

(defn user-thumb
  [avatar-href & [props]]
  [bs/image
   (merge {:src (right-href avatar-href)
           :height "42" :width "42"
           :circle true}
          props)])

(defn user-selection-component
  [{username :username {href :href} :avatar}]
  (fn [{username :username {href :href} :avatar}]
    [:div username
     [:span
      {:style {:padding-left "10px"}}
      [user-thumb href {:height "25px" :width "25px"}]]]))

(defn number-cell [n]
  (fn [n]
    [:td n]))

(defn dummy-cell []
  (fn []
    [:td ""]))

(defn prepend-cell
  "prepend a cell `child` to a seq of siblings (useful for prepending :td in a :tr)"
  [siblings {:keys [key child opts]}]
  (vec (cons ^{:key k} (apply merge [child] opts) siblings)))

(defn notification-child
  [message date status href]
  [:div.notification
   {:class "success"}
   [:div.illustration
    [user-thumb href]]
   [:div.text
    [:div.title message]
    [:div.text (.toLocaleString date "en-US")]]])

(defn notification
  [{id :id {message :message date :date {href :href} :by status :status} :data}]
  (fn [{id :id {message :message date :date by :by status :status} :data}]
    [:li#notification
     {:on-click #(re-frame/dispatch [:drop-notification id])}
     [notification-child message date (or status :info) href]]))

(defn notification-container []
  (let [notifications (re-frame/subscribe [:notifications])]
    (fn []
      [:ul#notifications
       [css-transition-group
        {:transition-name "notification"
         :transition-enter-timeout 650
         :transition-leave-timeout 650}
        (map (fn [{:keys [id data]}]
               ^{:key id} [notification {:id id :data data}])
             @notifications)]])))

(defn filter-annotation-btn [username filtered & [opts]]
  (let [user (re-frame/subscribe [:user username])]
    (fn [username filtered]
      (let [{{href :href} :avatar} @user]
        [bs/overlay-trigger
         {:overlay (reagent/as-component [bs/tooltip {:id "tooltip"} username])
          :placement "bottom"}
         [bs/button
          (merge
           {:active (boolean filtered)
            :style {:max-height "40px"}
            :onClick #(re-frame/dispatch [:update-filtered-users username (not filtered)])}
           opts)
          (reagent/as-component [user-thumb href {:height "20px" :width "20px"}])]]))))

(defn filter-annotation-buttons []
  (let [filtered-users (re-frame/subscribe [:active-project :filtered-users])
        active-project-users (re-frame/subscribe [:active-project-users])]
    (fn []
      [bs/button-toolbar
       (doall (for [{:keys [username]} @active-project-users
                    :let [filtered (contains? @filtered-users username)]]
                ^{:key username} [filter-annotation-btn username filtered]))])))

(defn default-header [] [:div ""])

(defn minimize-panel
  [{:keys [child args init open-header closed-header]
    :or {init true open-header default-header close-header default-header}}]
  (let [open (reagent/atom init)]
    (fn [{:keys [child init args]}]
      [bs/panel
       {:collapsible true
        :expanded @open
        :header (reagent/as-component
                 [:div.container-fluid
                  [:div.row
                   [:span.pull-right
                    [bs/button {:onClick #(swap! open not) :bsSize "xsmall"}
                     [bs/glyphicon
                      {:glyph (if @open "triangle-top" "triangle-bottom")}]]]
                   (if @open [open-header] [closed-header])]])}
       (into [child] args)])))

(defn disabled-button-tooltip [disabled?-fn msg]
  (if (disabled?-fn)
    (reagent/as-component [bs/tooltip {:id "tooltip"} msg])
    (reagent/as-component [:span])))

(defn load-from-ls-row [backup]
  [:tr
   [:td
    {:style {:cursor "pointer"}
     :on-click #(let [dump (ls/recover-db backup)]
                  (re-frame/dispatch [:load-db dump])
                  (re-frame/dispatch [:close-modal :localstorage]))}
    (date-str->locale backup)]])

(defn load-from-ls-modal [open?]
  (fn [open?]
    [bs/modal
     {:show @open?
      :on-hide #(re-frame/dispatch [:close-modal :localstorage])}
     [bs/modal-header
      {:closeButton true}
      [bs/modal-title
       [:div [:span {:style {:padding-right "20px"}} [:i.zmdi.zmdi-storage]]
        "Application history"]]]
     [bs/modal-body
      (let [history (ls/recover-all-db-keys)]
        (if (empty? history)
          [:div.text-muted "No backups have been found"]
          [:div
           [:div.text-muted "Application state backups: choose one to time-travel to."]
           [:br]
           [bs/table
            {:hover true}
            [:thead]
            [:tbody
             (for [backup history
                   :let [timestamp (.parse js/Date backup)]]
               ^{:key timestamp} [load-from-ls-row backup])]]]))]]))
