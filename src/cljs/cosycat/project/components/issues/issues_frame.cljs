(ns cosycat.project.components.issues.issues-frame
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [react-bootstrap.components :as bs]
            [clojure.string :refer [capitalize]]
            [cosycat.utils :refer [->map human-time]]
            [cosycat.app-utils :refer [dekeyword]]
            [cosycat.components :refer [dropdown-select user-thumb css-transition-group]]
            [cosycat.project.components.issues.annotation-issue-component
             :refer [annotation-issue-component]]
            [cosycat.project.components.issues.remove-project-component
             :refer [remove-project-component]]
            [taoensso.timbre :as timbre]))

(declare issue-component)

(defn issuer-thumb [& {:keys [username] :or {username :me}}]
  (let [href (if (= username :me)
                 (re-frame/subscribe [:me :avatar :href])
                 (re-frame/subscribe [:user username :avatar :href]))]
    (fn [& opts]
      [user-thumb {:style {:margin "10px"}} (or @href "img/avatars/server.png")])))

(defn issue-timestamp
  [username timestamp {resolved-by :by resolved-when :timestamp status :status :as resolve-data}]
  [:span.text-muted
   {:style {:margin-left "30px" :font-size "14px"}}
   [:span "Issued by " [:strong username] " " (human-time timestamp)]
   (when resolve-data
     [:span ". " (capitalize status) " by " [:strong resolved-by] " " (human-time resolved-when)])])

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

(def issue-type->description
  {:delete-project-agree "Delete project"
   :annotation-edit "Suggestion to edit annotation"
   :annotation-remove "Suggestion to remove annotation"})

(def description->issue-name* (reduce-kv (fn [m k v] (assoc m v k)) {} issue-type->description))
(defn description->issue-type [description]
  (get description->issue-name* description))

(defn issue-container [issue]
  (fn [{data :data timestamp :timestamp status :status by :by type :type resolve :resolve :as issue}]
    [bs/list-group-item
     (reagent/as-component
      [:div.container-fluid
       [:div.row
        [:div.col-lg-10.col-sm-10
         [:div.container-fluid
          [:div.row
           [:h4 [:span [status-icon status]] (issue-type->description (keyword type))]
           [issue-timestamp by timestamp resolve]]]]
        [:div.col-lg-2.col-sm-2.text-right [issuer-thumb :username by]]]
       [:div.row {:style {:height "10px"}}]
       [:div.row {:style {:margin-left "10px"}} [issue-component issue]]])]))

(defmulti issue-component (fn [{issue-type :type}] (keyword issue-type)))
(defmethod issue-component :annotation-edit [issue] [annotation-issue-component issue])
(defmethod issue-component :annotation-remove [issue] [annotation-issue-component issue])
(defmethod issue-component :delete-project-agree [issue] [remove-project-component issue])
(defmethod issue-component :default [issue] [:div (str issue)])

(defn issue-filter [issues]
  (let [status-filter (re-frame/subscribe [:project-session :components :issue-filters :status])
        type-filter (re-frame/subscribe [:project-session :components :issue-filters :type])]
    (fn []
      [bs/button-toolbar
       [dropdown-select
        {:label "status: "
         :header "Filter issues by status"
         :model @status-filter
         :options (map #(->map % %) ["open" "closed" "all"])
         :select-fn #(re-frame/dispatch
                      [:set-project-session-component [:issue-filters :status] %])}]
       (let [options (->> (vals @issues) (mapv :type) (into #{"all"}))]
         [dropdown-select
          {:label "type: "
           :header "Filter issues by type"
           :model @type-filter
           :options (map #(->map % %) (vec options))
           :select-fn #(re-frame/dispatch
                        [:set-project-session-component [:issue-filters :type] %])}])])))

(defn should-display-issue?
  [{issue-status :status issue-type :type} {status-filter :status type-filter :type}]
  (and (or (= "all" status-filter) (= issue-status status-filter))
       (or (= "all" type-filter)   (= issue-type   type-filter))))

(defn issues-panel [issues]
  (let [issue-filters (re-frame/subscribe [:project-session :components :issue-filters])]
    (fn [issues]
      [bs/list-group
       [css-transition-group {:transition-name "updates"
                              :transition-enter-timeout 650
                              :transition-leave-timeout 650}
        (doall (for [{:keys [id] :as issue} (sort-by :timestamp > (vals @issues))
                     :when (should-display-issue? issue @issue-filters)]
                 ^{:key id} [issue-container issue]))]])))

(defn has-open-issues? [status-filter issues]
  (and (= status-filter "open") (empty? (filter #(= "open" (:status %)) issues))))

(defn issues-frame []
  (let [issues (re-frame/subscribe [:active-project :issues])
        status-filter (re-frame/subscribe [:project-session :components :issue-filters :status])]
    (fn []
      [:div.container-fluid
       [:div.row.pull-right [:div.col-lg-12 [issue-filter issues]]]
       [:div.row {:style {:height "50px"}}]
       [:div.row
        [:div.col-lg-12
         (if (has-open-issues? @status-filter (vals @issues))
           [:div.text-center [:h2.text-muted "No open issues"]]
           [issues-panel issues])]]])))

