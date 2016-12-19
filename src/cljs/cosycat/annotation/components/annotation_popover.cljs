(ns cosycat.annotation.components.annotation-popover
  (:require [react-bootstrap.components :as bs]
            [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [cosycat.utils :refer [human-time by-id]]
            [cosycat.roles :refer [may-edit?]]
            [cosycat.components :refer [user-thumb]]
            [taoensso.timbre :as timbre]))

(defn dispatch-update
  [{:keys [_id _version username] :as ann-map} new-value hit-id my-name my-role db-path]
  (if (may-edit? :update username my-name my-role)
    ;; dispatch update
    (re-frame/dispatch
     [:update-annotation
      {:update-map {:_id _id :_version _version :value new-value :hit-id hit-id}
       :db-path db-path}])
    ;; dispatch update edit
    (re-frame/dispatch [:open-annotation-edit-issue (assoc ann-map :value new-value)])))

(defn dispatch-remove [{:keys [username] :as ann-map} hit-id my-name my-role db-path]
  (if (may-edit? :update username my-name my-role)
    ;; dispatch remove
    (re-frame/dispatch
     [:delete-annotation
      {:ann-data {:ann-map ann-map :hit-id hit-id}
       :db-path db-path}])
    ;; dispatch remote edit
    (re-frame/dispatch [:open-annotation-remove-issue ann-map])))

(defn trigger-update [ann-map new-value hit-id my-name my-role db-path & [on-dispatch]]
  (fn [e]
    (when (= 13 (.-charCode e))
      (on-dispatch)
      (if (empty? new-value)
        (dispatch-remove ann-map hit-id my-name my-role db-path)
        (dispatch-update ann-map new-value hit-id my-name my-role db-path)))))

(defn new-value-input
  [{{value :value} :ann} hit-id my-name my-role db-path on-dispatch]
  (let [value-atom (reagent/atom value)
        clicked (reagent/atom false)]
    (fn [{{key :key value :value} :ann username :username :as ann-map}
         hit-id my-name my-role db-path on-dispatch]
      (let [may-edit (may-edit? :update username my-name my-role)
            tooltip-text (if may-edit "Click to modify" "Click to suggest a modification")]
        [:div
         [:span {:style {:padding-left "5px"}} key]
         [:span {:style {:text-align "left" :margin-left "7px"}}
          (if-not @clicked
            [bs/overlay-trigger
             {:overlay (reagent/as-component [bs/tooltip {:id "tooltip"} tooltip-text])
              :placement "right"}
             [bs/label
              {:onClick #(swap! clicked not)
               :bsStyle (if may-edit "primary" "warning")
               :style {:cursor "pointer" :float "right" :font-size "100%"}}
              value]]
            [:input.input-as-div
             {:name "newannval"
              :type "text"
              :value @value-atom
              :on-key-press
              (trigger-update ann-map @value-atom hit-id my-name my-role db-path on-dispatch)
              :on-blur #(do (reset! value-atom value) (swap! clicked not))
              :on-change #(reset! value-atom (.. % -target -value))}])]]))))

(defn history-row [ann-map current-ann hit-id my-name my-role on-dispatch & {:keys [editable?]}]
  (fn [{{value :value} :ann timestamp :timestamp username :username} current-ann hit-id on-dispatch
       & {:keys [editable?]}]
    (let [may-edit (may-edit? :update (:username current-ann) my-name my-role)
          tooltip-text (if may-edit
                         "Click to restore this version"
                         "Click to suggest revert to this version")]
      [:tr
       [:td (if-not editable?
              [bs/label value]
              [bs/overlay-trigger
               {:overlay (reagent/as-component [bs/tooltip {:id "tooltip"} tooltip-text])
                :placement "left"}
               [bs/label
                {:style {:cursor "pointer"}
                 :bsStyle (if may-edit "primary" "warning")
                 :onClick #(trigger-update current-ann hit-id value my-name my-role on-dispatch)}
                value]])]
       [:td {:style {:width "25px"}}]
       [:td
        [:span.text-muted username]
        [:span
         {:style {:margin-left "10px"}}
         (human-time timestamp)]]])))

(defn get-history [history]
   (butlast (interleave (sort-by :timestamp > history) (range))))

(defn history-body
  [{:keys [history] :as current-ann} hit-id my-name my-role on-dispatch & {:keys [editable?]}]
  (fn [{:keys [history] :as current-ann} hit-id on-dispatch & {:keys [editable?]}]
    [:tbody
     (doall
      (for [{{:keys [key value]} :ann timestamp :timestamp :as item} (get-history history)]
        (if value
          ^{:key (str value timestamp)}
          [history-row item current-ann hit-id my-name my-role on-dispatch :editable? editable?]
          ^{:key (str "spacer-" item)}
          [:tr {:style {:height "5px"}} [:td ""]])))]))

(defn key-val [{:keys [key value]}]
  [:div
   [:span {:style {:padding-left "5px"}} key]
   [:span {:style {:text-align "left" :margin-left "7px"}}
    [bs/label
     {:bsStyle "primary"
      :style {:float "right" :font-size "100%"}}
     value]]])

(defn annotation-popover
  [{:keys [ann-map hit-id on-dispatch editable? db-path] :or {editable? true}}]
  (let [{:keys [timestamp username history ann]} ann-map
        user (re-frame/subscribe [:user username])
        my-name (re-frame/subscribe [:me :username])
        my-role (re-frame/subscribe [:active-project-role])]
    [bs/popover
     {:id "popover"
      :title (reagent/as-component
              [:div.container-fluid
               {:style {:min-width "200px"}}
               [:div.row
                [:div.col-sm-4.pad
                 {:style {:padding-left "0px"}}
                 [user-thumb (get-in @user [:avatar :href])]]
                [:div.col-sm-8.pad
                 [:div.container-fluid
                  [:div.row.pad.pull-right [:div.text-muted username]]
                  [:div.row.pad {:style {:height "25px"}}]
                  [:div.row.pad.pull-right (human-time timestamp)]]]]])
      :style {:max-width "100%"}}
     [:div.container-fluid
      [:div.row {:style {:background-color "#e2e2e2"}}
       (if editable?
         [new-value-input ann-map hit-id @my-name @my-role db-path on-dispatch]
         [key-val ann])]
      [:div.row {:style {:height "8px"}}]
      [:div.row
       [:table
        (when-not (empty? history)
          [history-body ann-map hit-id @my-name @my-role on-dispatch :editable? editable?])]]]]))
