(ns cosycat.annotation.components.annotation-popover
  (:require [react-bootstrap.components :as bs]
            [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [cosycat.utils :refer [human-time by-id]]
            [cosycat.roles :refer [check-annotation-role]]
            [cosycat.components :refer [user-thumb]]
            [taoensso.timbre :as timbre]))

(defn may-edit? [action username my-name my-role]
  (let [role (if (= username my-name) "owner" my-role)]
    (check-annotation-role action role)))

(defn dispatch-update [{:keys [_id _version username history]} hit-id new-value my-name my-role]
  (if (may-edit? :update username my-name my-role)
    ;; dispatch update
    (re-frame/dispatch
     [:update-annotation
      {:update-map {:_id _id :_version _version :value new-value :hit-id hit-id}}])
    (let [users (vec (into #{my-name} (map :username history)))]
      ;; dispatch update edit
      (re-frame/dispatch
       [:open-annotation-edit
        {:_version _version :_id _id :hit-id hit-id :value new-value :users users}]))))

(defn dispatch-remove [{:keys [_id _version username history] :as ann-map} hit-id my-name my-role]
  (if (may-edit? :update username my-name my-role)
    ;; dispatch remove
    (re-frame/dispatch [:delete-annotation {:ann-map ann-map :hit-id hit-id}])
    ;; dispatch remote edit
    (let [users (vec (into #{my-name} (map :username history)))]
      (re-frame/dispatch
       [:open-annotation-remove
        {:_version _version :_id _id :hit-id hit-id :users users}]))))

(defn trigger-update [ann-map hit-id new-value my-name my-role & [on-dispatch]]
  (fn [e]
    (when (= 13 (.-charCode e))
      (on-dispatch)
      (if (empty? new-value)
        (dispatch-remove ann-map hit-id my-name my-role)
        (dispatch-update ann-map hit-id new-value my-name my-role)))))

(defn new-value-input [{{value :value} :ann} hit-id my-name my-role on-dispatch]
  (let [text-atom (reagent/atom value)
        clicked (reagent/atom false)]
    (fn [{{key :key value :value} :ann id :_id version :_version username :username time :timestamp :as ann}
         hit-id my-name my-role on-dispatch]
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
              :value  @text-atom
              :on-key-press (trigger-update ann hit-id @text-atom my-name my-role on-dispatch)
              :on-blur #(do (reset! text-atom value) (swap! clicked not))
              :on-change #(reset! text-atom (.. % -target -value))}])]]))))

(defn history-row [ann current-ann hit-id my-name my-role on-dispatch]
  (fn [{{value :value} :ann timestamp :timestamp}
       {version :_version id :_id username :username :as current-ann}
       hit-id on-dispatch]
    (let [may-edit (may-edit? :update username my-name my-role)
          tooltip-text (if may-edit "Click to restore this version" "Click to suggest revert to this version")]
      [:tr
       [:td [bs/overlay-trigger
             {:overlay (reagent/as-component [bs/tooltip {:id "tooltip"} tooltip-text])
              :placement "left"}
             [bs/label
              {:style {:cursor "pointer"}
               :bsStyle (if may-edit "primary" "warning")
               :onClick #(trigger-update current-ann hit-id value my-name my-role on-dispatch)}
              value]]]
       [:td {:style {:width "25px"}}]
       [:td
        [:span.text-muted username]
        [:span
         {:style {:margin-left "10px"}}
         (human-time timestamp)]]])))

(defn spacer-row [] [:tr {:style {:height "5px"}} [:td ""]])

(defn get-ann-history [history]
   (butlast (interleave (sort-by :timestamp > history) (range))))

(defn history-body [history current-ann hit-id my-name my-role on-dispatch]
  (fn [history current-ann hit-id on-dispatch]
    [:tbody
     (doall
      (for [{{value :value} :ann timestamp :timestamp :as ann} (get-ann-history history)
            :let [key (if value (str value timestamp) (str "spacer-" ann))]]
        (if value
          ^{:key key} [history-row ann current-ann hit-id my-name my-role on-dispatch]
          ^{:key key} [spacer-row])))]))

(defn annotation-popover
  [{{:keys [timestamp username history _version] :as ann} :ann-map
    hit-id :hit-id on-dispatch :on-dispatch}]
  (let [user (re-frame/subscribe [:user username])
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
       [new-value-input ann hit-id @my-name @my-role on-dispatch]]
      [:div.row {:style {:height "8px"}}]
      [:div.row [:table (when-not (empty? history)
                          [history-body history ann hit-id @my-name @my-role on-dispatch])]]]]))
