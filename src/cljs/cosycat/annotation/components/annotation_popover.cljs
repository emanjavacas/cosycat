(ns cosycat.annotation.components.annotation-popover
  (:require [react-bootstrap.components :as bs]
            [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [cosycat.utils :refer [human-time by-id]]
            [cosycat.components :refer [user-thumb]]
            [taoensso.timbre :as timbre]))

(defn dispatch-update [id version new-value hit-id on-dispatch]
  (re-frame/dispatch
   [:update-annotation
    {:update-map {:_id id :_version version :value new-value :hit-id hit-id}}])
  (on-dispatch))

(defn trigger-update [id version new-value hit-id on-dispatch]
  (fn [e]
    (when (= 13 (.-charCode e))
      (dispatch-update id version new-value hit-id on-dispatch))))

(defn new-value-input [{{key :key value :value} :ann} hit-id on-dispatch]
  (let [text-atom (reagent/atom value)
        clicked (reagent/atom false)]
    (fn [{{key :key value :value} :ann
          id :_id version :_version user :username time :timestamp} hit-id]
      [:div
       [:span {:style {:padding-left "5px"}} key]
       [:span {:style {:text-align "left" :margin-left "7px"}}
        (if-not @clicked
          [bs/overlay-trigger
           {:overlay (reagent/as-component [bs/tooltip {:id "tooltip"} "Click to modify"])
            :placement "right"}
           [bs/label
            {:onClick #(swap! clicked not)
             :bsStyle "primary"
             :style {:cursor "pointer" :float "right" :font-size "100%"}}
            value]]
          [:input.input-as-div
           {:name "newannval"
            :type "text"
            :value  @text-atom
            :on-key-press (trigger-update id version @text-atom hit-id on-dispatch)
            :on-blur #(do (reset! text-atom value) (swap! clicked not))
            :on-change #(reset! text-atom (.. % -target -value))}])]])))

(defn history-row [ann current-ann hit-id on-dispatch]
  (fn [{{value :value} :ann timestamp :timestamp username :username}
       {version :_version id :_id :as current-ann}
       hit-id on-dispatch]
    [:tr
     [:td [bs/overlay-trigger
           {:overlay (reagent/as-component
                      [bs/tooltip {:id "tooltip"} "Click to restore this version"])
            :placement "left"}
           [bs/label
            {:style {:cursor "pointer"}
             :bsStyle "primary"
             :onClick #(dispatch-update id version value hit-id on-dispatch)}
            value]]]
     [:td {:style {:width "25px"}}]
     [:td
      [:span.text-muted username]
      [:span
       {:style {:margin-left "10px"}}
       (human-time timestamp)]]]))

(defn spacer-row [] [:tr {:style {:height "5px"}} [:td ""]])

(defn history-body [history current-ann hit-id on-dispatch]
  (fn [history current-ann hit-id on-dispatch]
    [:tbody
     (doall
      (for [{{value :value} :ann timestamp :timestamp :as ann}
            (butlast (interleave (sort-by :timestamp > history) (range)))
            :let [key (if value (str value timestamp) (str "spacer-" ann))]]
        (if value
          ^{:key key} [history-row ann current-ann hit-id on-dispatch]
          ^{:key key} [spacer-row])))]))

(defn annotation-popover
  [{{:keys [timestamp username history _version] :as ann} :ann-map
    hit-id :hit-id on-dispatch :on-dispatch}]
  (let [user (re-frame/subscribe [:user username])]
    [bs/popover
     {:id "popover"
      :title (reagent/as-component
              [:div.container-fluid
               [:div.row
                [:div.col-sm-4
                 {:style {:padding-left "0px"}}
                 [user-thumb (get-in @user [:avatar :href])]]
                [:div.col-sm-8
                 [:div.row.pull-right [:div.text-muted username]]
                 [:br] [:br]
                 [:div.row.pull-right (human-time timestamp)]]]])
      :style {:max-width "100%"}}
     [:div.container-fluid
      [:div.row {:style {:background-color "#e2e2e2"}} [new-value-input ann hit-id on-dispatch]]
      [:div.row {:style {:height "8px"}}]
      [:div.row [:table (when-not (empty? history)
                          [history-body history ann hit-id on-dispatch])]]]]))
