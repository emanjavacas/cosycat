(ns cleebo.front.components.users-input
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [react-bootstrap.components :as bs]
            [cleebo.components :refer [user-selection-component]]
            [cleebo.front.components.include-box :refer [include-box-component]]
            [cleebo.roles :refer [project-user-roles]]
            [cleebo.utils :refer [parse-time human-time]]
            [taoensso.timbre :as timbre]))

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
  (let [users (re-frame/subscribe [:users :exclude-me true])]
    (fn [selected-users]
      (when-not (empty? @users)
        [:div (doall (for [row (partition-all 3 @users)
                           {:keys [username] :as user} row]
                       ^{:key username}
                       [:div.col-lg-4 [user-profile user selected-users]]))]))))
