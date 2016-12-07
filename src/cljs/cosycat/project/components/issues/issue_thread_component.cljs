(ns cosycat.project.components.issues.issue-thread-component
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [react-bootstrap.components :as bs]
            [cosycat.utils :refer [human-time]]
            [cosycat.app-utils :refer [disjconj]]
            [cosycat.components :refer [user-thumb]]
            [cosycat.project.components.issues.components :refer [collapsible-issue-panel]]
            [taoensso.timbre :as timbre]))

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

(defn comment-component [{:keys [comment timestamp by deleted] :as comment-map} issue-id & {:keys [commentable?]}]
  (let [href (re-frame/subscribe [:user by :avatar :href])
        highlighted "rgba(227, 227, 227, 0.5)"
        my-name (re-frame/subscribe [:me :username])
        show-comment-input? (reagent/atom false)]
    (fn [{:keys [comment timestamp by id deleted] :as comment-map} issue-id & {:keys [commentable?]}]
      [:div.panel.panel-default {:style {:border-width "1px" :margin-bottom "0"}}
       [:div.panel-body
        {:style {:padding "10px" :background-color (when @show-comment-input? highlighted)}}
        [:div.container-fluid
         (if deleted
           "Comment deleted by the author"
           [:div.row
            {:style {:min-height "35px"}}
            [:div.col-lg-1.col-md-1.col-sm-1.pad [user-thumb {:width "30px" :height "30px"} @href]]
            [:div.col-lg-11.col-md-11.col-sm-11.pad {:style {:white-space "pre-wrap"}} comment]])
         (when-not deleted [:div.row {:style {:height "10px"}}])
         (when-not deleted
           [:div.row
            [:div.col-lg-6.col-sm-6.text-left.pad
             [:span.text-muted  "by " [:strong by] " "(human-time timestamp)]]
            (let [style {:cursor "pointer" :padding "0 5px"}]
              [:div.col-lg-6.col-sm-6.text-right.pad
               (when (and (= by @my-name))
                 [:a {:style style
                      :onClick #(re-frame/dispatch
                                 [:delete-comment-on-issue {:comment-id id :issue-id issue-id}])}
                  "Delete"])
               [:a {:style style
                    :onClick #(swap! show-comment-input? not)}
                (if (and commentable? @show-comment-input?) "Dismiss" "Reply")]])])
         (when (and commentable? @show-comment-input?)
           [:div.row {:style {:height "10px"}}])
         (when (commentable? @show-comment-input?)
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

(defn thread-component [{issue-id :id comments :comments :as issue} & {:keys [commentable?]}]
  (fn [{issue-id :id comments :comments :as issue} & {:keys [commentable?]}]
    [:div.container-fluid
     (doall (for [[depth comment-id] (comments->tree (vals comments))
                  :let [{:keys [id] :as comment-map} (get comments (keyword comment-id))]]
              ^{:key id} [:div.row
                          {:style {:padding-left (when (pos? depth) (str (* 20 depth) "px"))}}
                          [comment-component comment-map issue-id :commentable? commentable?]]))]))

(defn issue-thread [issue & {:keys [commentable?]}]
  (fn [{comments :comments issue-id :id :as issue} & {:keys [commentable?]}]
    [:div.container-fluid
     (when commentable? [:div.row [thread-comment-input issue-id]])
     [:div.row {:style {:height "10px"}}]
     (when comments [:div.row [thread-component issue :commentable? commentable?]])]))

(defn issue-thread-component [issue & {:keys [collapsible? commentable?]}]
  (fn [{:keys [comments] :as issue} & {:keys [collapsible?] :or {collapsible? true commentable? true}}]
    (let [deleted-comments (count (filter :deleted (vals comments)))
          valid-comments (- (count comments) deleted-comments)
          title (str "Show thread (" valid-comments " comments)")]
      (if collapsible?
        [collapsible-issue-panel title issue-thread issue :show-thread]
        [issue-thread issue :commentable? commentable?]))))
