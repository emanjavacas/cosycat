(ns cleebo.annotation.components.annotation-popover
  (:require [react-bootstrap.components :as bs]
            [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [cleebo.utils :refer [human-time by-id]]
            [cleebo.components :refer [user-thumb]]
            [taoensso.timbre :as timbre]))

(defn trigger-update [id version new-value hit-id]
  (fn [e]
    (when (= 13 (.-charCode e))
      (re-frame/dispatch
       [:update-annotation
        {:update-map {:_id id :_version version :value new-value}
         :hit-id hit-id}]))))

(defn key-val [{{key :key value :value} :ann} hit-id]
  (let [text-atom (reagent/atom value)
        clicked (reagent/atom false)]
    (fn [{{key :key value :value} :ann
          id :_id version :_version user :username time :timestamp} hit-id]
      [:div key
       [:span {:style {:text-align "right" :margin-left "7px"}}
        (if-not @clicked
          [bs/label
           {:onClick #(swap! clicked not)
            :style {:cursor "pointer" :text-align "right"}} value]
          [:input.input-as-div
           {:name "newannval"
            :type "text"
            :value  @text-atom
            :on-key-press (trigger-update id version @text-atom hit-id)
            :on-blur #(do (reset! text-atom value) (swap! clicked not))
            :on-input #(reset! text-atom (.. % -target -value))}])]])))

(defn history-body [history]
  (fn [history]
    [:tbody
     (doall
      (for [{{value :value} :ann :as ann} (sort-by :timestamp > history)]
        ^{:key (str value (:timestamp ann))}
        [:tr {:style {:padding "50px"}}
         [:td [bs/label value]]
         [:td {:style {:width "25px"}}]
         [:td
          [:span.text-muted (:username ann)]
          [:span
           {:style {:margin-left "10px"}}
           (human-time (:timestamp ann))]]]))]))

(defn annotation-popover
  [{time :timestamp username :username history :history :as ann} hit-id]
  (let [user (re-frame/subscribe [:user username])]
    (reagent/as-component
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
                  [:div.row.pull-right (human-time time)]]]])
       :style {:max-width "100%"}}
      [:div.container-fluid
       [:div.row [key-val ann hit-id]]
       [:div.row ]
       [:div.row [:table (when-not (empty? history) [history-body history])]]]])))
