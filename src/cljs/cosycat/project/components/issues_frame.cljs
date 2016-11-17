(ns cosycat.project.components.issues-frame
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [react-bootstrap.components :as bs]
            [cosycat.components :refer [append-cell prepend-cell]]
            [cosycat.utils :refer [->map human-time]]
            [cosycat.app-utils :refer [dekeyword disjconj]]
            [cosycat.components :refer [dropdown-select user-thumb css-transition-group]]
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

(defn show-hit-panel [{{hit-map :hit-map} :meta :as issue}]
  (reagent/create-class
   {:component-will-mount
    #(when-not hit-map (re-frame/dispatch [:fetch-issue-hit {:issue issue :context 10}]))
    :reagent-render
    (fn [{{new-value :value {:keys [value key]} :ann} :data {{:keys [hit]} :hit-map} :meta}]
      [:div (doall (for [{:keys [id word match]} hit]
                     ^{:key id}
                     [:span
                      {:style {:padding "0 5px"}}
                      (if match [:strong word] word)]))])}))

(defn dispatch-comment
  ([value issue-id parent-id]
   (when-not (empty? @value)
     (re-frame/dispatch
      [:comment-on-issue {:comment @value :issue-id issue-id :parent-id parent-id}])))
  ([value issue-id parent-id keys-pressed]
   (when (and (contains? @keys-pressed 13) (not (contains? @keys-pressed 16)))
     (dispatch-comment value issue-id parent-id))))

(defn thread-comment-input [issue-id & {:keys [parent-id]}]
  (let [href (re-frame/subscribe [:me :avatar :href])
        keys-pressed (atom #{})
        value (reagent/atom "")]
    (fn [issue-id & {:keys [parent-id]}]
      [:div.input-group
       [:span.input-group-addon
        [:img.img-rounded {:src @href :width "30px"}]]
       [:textarea.form-control.form-control-no-border
        {:type "text"
         :style {:resize "none"}
         :placeholder "..."
         :value @value
         :on-key-up #(swap! keys-pressed disjconj (.-keyCode %))
         :on-key-down #(swap! keys-pressed disjconj (.-keyCode %))
         :on-key-press #(dispatch-comment value issue-id parent-id keys-pressed)
         :on-change #(reset! value (.-value (.-target %)))}]
       [:span.input-group-addon
        {:onClick #(dispatch-comment value issue-id parent-id)
         :style {:cursor "pointer"}}
        [bs/glyphicon {:glyph "send"}]]])))

(defn comment-component [{:keys [comment timestamp by] :as comment-map} issue-id]
  (let [href (re-frame/subscribe [:user by :avatar :href])
        highlighted "rgba(227, 227, 227, 0.5)"
        my-name (re-frame/subscribe [:me :username])
        show-comment-input? (reagent/atom false)]
    (fn [{:keys [comment timestamp by id] :as comment-map} issue-id]
      [:div.panel.panel-default {:style {:border-width "1px" :margin-bottom "0"}}
       [:div.panel-body
        {:style {:padding "10px" :background-color (when @show-comment-input? highlighted)}}
        [:div.container-fluid
         [:div.row
          {:style {:min-height "35px"}}
          [:div.col-lg-1.col-md-1.col-sm-1.pad [user-thumb {:width "30px" :height "30px"} @href]]
          [:div.col-lg-11.col-md-11.col-sm-11.pad {:style {:white-space "pre-wrap"}} comment]]
         [:div.row {:style {:height "10px"}}]
         [:div.row
          [:div.col-lg-6.col-sm-6.text-left.pad
           [:span.text-muted  "by " [:strong by] " "(human-time timestamp)]]
          (let [style {:cursor "pointer" :padding "0 5px"}]
            [:div.col-lg-6.col-sm-6.text-right.pad
             (when (= by @my-name)
               [:a {:style style
                    :onClick #(re-frame/dispatch [:notify {:message "Not implememented yet"}])}
                "Delete"])
             [:a {:style style
                  :onClick #(swap! show-comment-input? not)}
              (if @show-comment-input? "Dismiss" "Reply")]])]
         (when @show-comment-input?
           [:div.row {:style {:height "10px"}}])
         (when @show-comment-input?
           [:div.row [thread-comment-input issue-id :parent-id id]])]]])))

(defn comments->tree
  "seq of tuples (depth comment-id) in depth-first order.
   Same depth comments are sorted according to `:timestamp`"
  [comments]
  (let [comment-ids (map :id comments)
        comments-by-id (zipmap comment-ids comments)
        roots (remove (into (hash-set) (mapcat :children comments)) comment-ids)
        rec (fn rec [depth remaining parents]
              (lazy-seq
               (when-not (empty? remaining)
                 (for [{:keys [children id]} (sort-by :timestamp < (map remaining parents))]
                   (cons [depth id] (rec (inc depth) (dissoc remaining id) children))))))]
    (->> (rec 0 comments-by-id roots)
         flatten ;; flatten to single items
         (partition 2)))) ;; partition to (depth comment-id) tuples

(defn thread-component [{issue-id :id comments :comments :as issue}]
  (fn [{issue-id :id comments :comments :as issue}]
    [:div.container-fluid
     (doall (for [[depth comment-id] (comments->tree (vals comments))
                  :let [{:keys [id] :as comment-map} (get comments (keyword comment-id))]]
              ^{:key id} [:div.row
                          {:style {:padding-left (when (pos? depth) (str (* 20 depth) "px"))}}
                          [comment-component comment-map issue-id]]))]))

(defn show-thread-panel [issue]
  (fn [{comments :comments issue-id :id :as issue}]
    [:div.container-fluid
     [:div.row [thread-comment-input issue-id]]
     [:div.row {:style {:height "10px"}}]
     (when comments [:div.row [thread-component issue]])]))

(defn annotation-edit-panel [header child {issue-id :id :as issue} panel-id]
  (let [expanded? (re-frame/subscribe [:project-session :components :issues issue-id panel-id])]
    (fn [header child issue panel-id]
      [:div.panel.panel-default
       {:style {:margin-bottom "5px"}}
       [:div.panel-heading
        {:on-click #(re-frame/dispatch [:toggle-project-session-component [:issues issue-id panel-id]])
         :style {:font-size "16px" :cursor "pointer"}}
        header]
       (when @expanded? [:div.panel-body [child issue]])])))

(defmethod issue-component :annotation-edit
  [{ann-data :data {:keys [hit-map]} :meta issue-id :id :as issue}]
  (fn [{ann-data :data meta :meta issue-id :id :as issue}]
    [:div.container-fluid
     [annotation-edit-panel "Show hit" show-hit-panel issue :show-hit]
     [annotation-edit-panel "Show thread" show-thread-panel issue :show-thread]]))

(defmethod issue-component :default
  [issue]
  (fn [issue]
    [:div (str issue)]))

(defn issues-panel [issues]
  (fn [issues]
    [bs/list-group
     [css-transition-group {:transition-name "updates"
                            :transition-enter-timeout 650
                            :transition-leave-timeout 650}
      (doall (for [{:keys [id] :as issue} (sort-by :timestamp > (vals @issues))]
               ^{:key id} [issue-container issue]))]]))

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
                  :select-fn #(re-frame/dispatch
                               [:set-project-session-component % :issue-filters label])}]))])))

(defn issues-frame []
  (let [issues (re-frame/subscribe [:active-project :issues])]
    (fn []
      [:div.container-fluid
       [:div.row.pull-right [:div.col-lg-12 [issue-filter issues]]]
       [:div.row {:style {:height "50px"}}]
       [:div.row
        [:div.col-lg-12
         [issues-panel issues]]]])))

