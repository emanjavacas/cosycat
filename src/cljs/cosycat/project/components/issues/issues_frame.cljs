(ns cosycat.project.components.issues.issues-frame
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [react-bootstrap.components :as bs]
            [cosycat.utils :refer [->map human-time]]
            [cosycat.app-utils :refer [dekeyword]]
            [cosycat.components :refer [dropdown-select user-thumb css-transition-group]]
            [cosycat.project.components.issues.annotation-edit-component
             :refer [annotation-edit-component]]
            [taoensso.timbre :as timbre]))

(declare issue-component)

(defn issuer-thumb [& {:keys [username] :or {username :me}}]
  (let [href (if (= username :me)
                 (re-frame/subscribe [:me :avatar :href])
                 (re-frame/subscribe [:user username :avatar :href]))]
    (fn [& opts]
      [user-thumb {:style {:margin "10px"}} (or @href "img/avatars/server.png")])))

(defn issue-timestamp [username timestamp]
  [:span.text-muted
   {:style {:margin-left "30px" :font-size "14px"}}
   [:span "Issued by " [:strong username] " " (human-time timestamp)]])

(defn status-icon [status]
  (let [green "#5cb85c", red "#d9534f"]
    (if (= "open" status)
      [bs/glyphicon
       {:glyph "remove-circle"
        :class "ignore"
        :style {:color red :margin-right "10px"}}]
      [bs/glyphicon
       {:glyph "ok-circle"
        :class "ignore"
        :style {:color green :margin-right "10px"}}])))

(defn issue-container [issue]
  (fn [{data :data timestamp :timestamp status :status by :by type :type :as issue}]
    [bs/list-group-item
     (reagent/as-component
      [:div.container-fluid
       [:div.row
        [:div.col-lg-10.col-sm-10
         [:div.container-fluid
          [:div.row
           [:h4 [:span [status-icon status]] (str type)]
           [issue-timestamp by timestamp]]]]
        [:div.col-lg-2.col-sm-2.text-right [issuer-thumb :username by]]]
       [:div.row {:style {:height "10px"}}]
       [:div.row {:style {:margin-left "10px"}} [issue-component issue]]])]))

(defmulti issue-component (fn [{issue-type :type}] (keyword issue-type)))
(defmethod issue-component :annotation-edit [issue] [annotation-edit-component issue])
(defmethod issue-component :default [issue] [:div (str issue)])

(defn issue-filter [issues]
  (let [status-sub (re-frame/subscribe [:project-session :components :issue-filters :status])
        type-sub (re-frame/subscribe [:project-session :components :issue-filters :type])]
    (fn []
      [bs/button-toolbar
       [dropdown-select
        {:label "status: "
         :header "Filter issues by status"
         :model @status-sub
         :options (map #(->map % %) ["open" "closed" "all"])
         :select-fn
         #(re-frame/dispatch [:set-project-session-component [:issue-filters :status] %])}]
       [dropdown-select
        {:label "type: "
         :header "Filter issues by type"
         :model @type-sub
         :options (map #(->map % %) (->> (vals @issues) (mapv :type) (into #{"all"}) vec))
         :select-fn
         #(re-frame/dispatch [:set-project-session-component [:issue-filters :type] %])}]])))

(defn issues-panel [issues]
  (fn [issues]
    [bs/list-group
     [css-transition-group {:transition-name "updates"
                            :transition-enter-timeout 650
                            :transition-leave-timeout 650}
      (doall (for [{:keys [id] :as issue} (sort-by :timestamp > (vals @issues))]
               ^{:key id} [issue-container issue]))]]))

(defn issues-frame []
  (let [issues (re-frame/subscribe [:active-project :issues])]
    (fn []
      [:div.container-fluid
       [:div.row.pull-right [:div.col-lg-12 [issue-filter issues]]]
       [:div.row {:style {:height "50px"}}]
       [:div.row
        [:div.col-lg-12
         [issues-panel issues]]]])))

