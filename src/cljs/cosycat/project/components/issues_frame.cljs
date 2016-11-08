(ns cosycat.project.components.issues-frame
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [react-bootstrap.components :as bs]
            [cosycat.utils :refer [->map human-time]]
            [cosycat.app-utils :refer [dekeyword]]
            [cosycat.components :refer [css-transition-group dropdown-select user-thumb]]
            [taoensso.timbre :as timbre]))

(defmulti issue-component (fn [{issue-type :type}] (keyword issue-type)))

(defn issuer-thumb [source-user]
  (let [avatar (re-frame/subscribe [:user source-user :avatar :href])]
    (fn [source-user]
      [:div {:style {:margin "10px"}}
       [user-thumb (or @avatar "img/avatars/server.png")]])))

(defn issue-timestamp [username timestamp]
  [:span.text-muted
   {:style {:margin-left "30px" :font-size "14px"}}
   [:span (str "Issued by ")]
   [:strong username]
   [:span {:style {:margin-left "7px"}} (human-time timestamp)]])

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

(defmethod issue-component :annotation-edit
  [issue]
  (fn [{{username :username :as data} :data timestamp :timestamp status :status}]
    [:div.container-fluid
     [:div.row
      [:div.col-lg-10
       [:div.container-fluid
        [:div.row
         [:h4 [:span [status-icon status]] "Edit annotation suggestion"]
         [issue-timestamp username timestamp]]]]
      [:div.col-lg-2.col-sm-2.text-right [issuer-thumb username]]]
     [:div.row {:style {:height "10px"}}]
     [:div "Show both hit versions"]]))

(defmethod issue-component :default
  [issue]
  [:div.container-fluid [:div.row (str issue)]])

(defn issue-item [issue]
  (fn [issue]
    [bs/list-group-item (reagent/as-component [issue-component issue])]))

(defn issues-panel [issues]
  (fn [issues]
    [bs/list-group
     [css-transition-group {:transition-name "updates"
                            :transition-enter-timeout 650
                            :transition-leave-timeout 650}
      (doall (for [{:keys [id] :as issue} (sort-by :timestamp > (vals @issues))]
               ^{:key id} [issue-item issue]))]]))

(defn issue-filter [issues]
  (let [status-sub (re-frame/subscribe [:project-session :components :issue-filters :status])
        type-sub (re-frame/subscribe [:project-session :components :issue-filters :type])
        open-filter {:options ["open" "closed" "all"]
                     :header "Filter issues by status"
                     :model status-sub
                     :label :status}
        type-filter {:options (->> (vals @issues) (mapv :type) (into #{"all"}) vec)
                     :model type-sub
                     :header "Filter issues by type"
                     :label :type}]
    (fn []
      [bs/button-toolbar
       (doall (for [{:keys [label model options header]} [open-filter type-filter]]
                ^{:key (str "filter-" label)}
                [dropdown-select
                 {:label (str (dekeyword label) ": ")
                  :header header
                  :model @model
                  :options (map #(->map % %) options)
                  :select-fn #(re-frame/dispatch [:set-project-session-component % :issue-filters label])}]))])))

(defn issues-frame []
  (let [issues (re-frame/subscribe [:active-project :issues])]
    (fn []
      [:div.container-fluid
       [:div.row.pull-right [:div.col-lg-12 [issue-filter issues]]]
       [:div.row {:style {:height "50px"}}]
       [:div.row
        [:div.col-lg-12
         [issues-panel issues]]]])))

