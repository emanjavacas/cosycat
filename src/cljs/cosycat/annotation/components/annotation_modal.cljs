(ns cosycat.annotation.components.annotation-modal
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [cosycat.utils :refer [by-id parse-annotation nbsp format wrap-key]]
            [cosycat.app-utils :refer [dekeyword]]
            [cosycat.roles :refer [check-annotation-role]]
            [cosycat.components :refer [disabled-button-tooltip]]
            [cosycat.autosuggest :refer [suggest-annotations]]
            [schema.core :as s]
            [react-bootstrap.components :as bs]
            [taoensso.timbre :as timbre]))

;;; Utils
(defn notify-not-authorized [action role]
  (let [action (dekeyword action)
        message (format "Your project role [%s] does not allow you to [%s] annotations"
                        role action)]
    (re-frame/dispatch [:notify {:message message}])))

(defn classify-annotation
  ([anns ann-key username] (classify-annotation (get anns ann-key) username))
  ([ann username]
   (cond (not ann)                    :empty-annotation
         (= username (:username ann)) :existing-annotation-owner
         :else                        :existing-annotation)))

(defn group-tokens [tokens ann-key username]
  (->> tokens (group-by #(classify-annotation (:anns %) ann-key username))))

(defn deselect-tokens [tokens]
  (doseq [{:keys [hit-id id]} tokens]
    (re-frame/dispatch [:unmark-token {:hit-id hit-id :token-id id}])))

;;; Dispatch operations
(defn dispatch-new-annotations [ann-map tokens]
  (re-frame/dispatch
   [:dispatch-bulk-annotation
    ann-map                    ;ann-map
    (->> tokens (map :hit-id)) ;hit-ids
    (->> tokens (map :id))]))  ;token-ids

(defn dispatch-annotation-updates [{{:keys [key value]} :ann :as ann-map} tokens]
  (doseq [{hit-id :hit-id {ann key} :anns} tokens
          :let [{:keys [_id _version]} ann]] ;get corresponding existing ann
    (re-frame/dispatch
     [:update-annotation
      {:update-map {:_id _id :_version _version :value value :hit-id hit-id}}])))

(defn suggest-annotation-edits [{ann-key :key new-value :value :as new-ann} anns me]
  (doseq [{{{:keys [_id _version ann span history]} ann-key} :anns hit-id :hit-id} anns
          :let [users (vec (into #{me} (map :username history)))
                ann-data {:_version _version :_id _id :hit-id hit-id :value new-value :span span}]
          ;; skip casses without real change
          :when (not= new-value (:value ann))]
    (re-frame/dispatch [:notify {:message "Edit not authorized"}])
    ;; TODO: think of a good way of doing the following:
    ;; (re-frame/dispatch [:open-annotation-edit-issue ann-data users])
    ))

(defn trigger-dispatch
  [action {:keys [value marked-tokens current-ann me my-role unselect? span-type]}]
  (when-let [new-ann (parse-annotation @value)]
    (let [{:keys [empty-annotation existing-annotation-owner existing-annotation]}
          (group-tokens @marked-tokens @current-ann @me)]
      (cond
        ;; shortcut if user is not authorized
        (not (check-annotation-role action @my-role)) (notify-not-authorized action @my-role)
        ;; user suggestion to change
        (and (empty? empty-annotation)
             (empty? existing-annotation-owner)
             (not (empty? existing-annotation)))
        (suggest-annotation-edits new-ann existing-annotation @me)
        ;; dispatch annotations
        :else (let [ann-map {:ann new-ann}]
                (dispatch-new-annotations ann-map empty-annotation)
                (dispatch-annotation-updates ann-map existing-annotation-owner)
                (when @unselect?
                  (do (deselect-tokens empty-annotation)
                      (deselect-tokens existing-annotation-owner)))))
      ;; finally
      (do (reset! value "")
          (reset! current-ann "")
          (re-frame/dispatch [:close-modal :annotation-modal])))))

;;; Components
(defn update-current-ann [current-ann value]
  (fn [target]
    (let [input-data @value
          [_ key] (re-find #"([^=]+)=?" input-data)]
      (reset! current-ann key))))

(defn on-key-press [opts]
  (wrap-key 13 (fn [] (trigger-dispatch :write opts))))

(defn annotation-input [marked-tokens opts]
  (let [tagsets (re-frame/subscribe [:selected-tagsets])]
    (fn [marked-tokens {:keys [value current-ann] :as opts}]
      [:table
       {:width "100%"}
       [:tbody
        [:tr
         [:td [:i.zmdi.zmdi-edit]]
         [:td [suggest-annotations @tagsets
               {:on-change (update-current-ann current-ann value)
                :class "form-control form-control-no-border"
                :placeholder "Format: key=value"
                :value value
                :onKeyDown #(.stopPropagation %)
                :onKeyPress (on-key-press opts)}]]]]])))

(defmulti existing-annotation-label (fn [ann me] (classify-annotation ann me)))

(defmethod existing-annotation-label :empty-annotation
  [ann me]
  [:span (nbsp)])

(defmethod existing-annotation-label :existing-annotation-owner
  [{username :username {value :value} :ann} me]
  [:span (str value)])

(defmethod existing-annotation-label :existing-annotation
  [{username :username {value :value} :ann} me]
  (fn [{username :username {value :value} :ann} me]
    [bs/overlay-trigger
     {:overlay (reagent/as-component [bs/tooltip {:id "tooltip"} (str "by: " username)])
      :placement "right"}
     [:span (str value)]]))

(defn background-color [ann me]
  (let [danger  "#fbeded", success "#def0de"]
    (cond
      (not ann) "white"
      (= (:username ann) me) success
      :else danger)))

(defn token-counts-row [[word ann :as token] cnt me]
  (fn [[word ann] cnt me]
    [:tr
     {:style {:background-color (background-color ann @me)}}
     [:td {:style {:padding-bottom "5px"}} word]
     [:td {:style {:padding-bottom "5px"}} [existing-annotation-label ann @me]]
     [:td {:style {:padding-bottom "5px" :text-align "right"}}
      [bs/label {:style {:vertical-align "-30%" :display "inline-block" :font-size "100%"}}
       cnt]]]))

(defn count-selected [marked-tokens current me]
  (->> @marked-tokens
       (sort-by (juxt :word (fn [{:keys [anns]}] (classify-annotation anns @current @me))))
       (map (juxt :word (fn [token] (get-in token [:anns @current]))))
       frequencies))

(defn token-counts-table [marked-tokens {:keys [current-ann me]}]
  (fn [marked-tokens {:keys [current-ann me]}]
    [:table
     {:width "100%"}
     [:thead
      [:tr
       [:th {:style {:padding-bottom "10px" :text-align "left"}}  "Token"]
       [:th {:style {:padding-bottom "10px" :text-align "left"}}  "Existing annotation"]
       [:th {:style {:padding-bottom "10px" :text-align "right"}} "Count"]]]
     [:tbody
      {:style {:font-size "14px !important"}}
      (for [[[word ann :as token] cnt] (count-selected marked-tokens current-ann me)]
        ^{:key (str word (:username ann) "pop")}
        [token-counts-row token cnt me])]]))

(defn annotation-modal [marked-tokens current-ann]
  (let [me (re-frame/subscribe [:me :username])
        show? (re-frame/subscribe [:modals :annotation-modal])
        my-role (re-frame/subscribe [:active-project-role])
        unselect? (reagent/atom false)
        span-type (reagent/atom "token")
        value (reagent/atom "")]
    (fn [marked-tokens current-ann]
      (let [opts {:value value
                  :me me
                  :my-role my-role
                  :current-ann current-ann
                  :unselect? unselect?
                  :span-type span-type
                  :marked-tokens marked-tokens}]
        [bs/modal
         {:class "large"
          :show @show?
          :on-hide #(re-frame/dispatch [:close-modal :annotation-modal])}
         [bs/modal-header
          {:closeButton true}
          [bs/modal-title
           {:style {:font-size "18px"}}
           "Tokens marked for annotation"]]
         [bs/modal-body
          [:div.container-fluid
           [:div.row
            [annotation-input marked-tokens opts]
            [:hr]
            [token-counts-table marked-tokens {:current-ann current-ann :me me}]]]]         
         [bs/modal-footer
          [:div.checkbox.pull-left
           [:label
            [:input
             {:type "checkbox"
              :checked @unselect?
              :on-change #(swap! unselect? not)}]
            [:span.text-muted "Unselect after dispatch?"]]]
          [bs/button
           {:className "pull-right"
            :bsStyle "info"
            :onClick #(trigger-dispatch :write opts)}
           "Submit"]]]))))

(defn annotation-modal-button []
  (let [marked-tokens (re-frame/subscribe [:marked-tokens])        
        current-ann (reagent/atom "")]
    (fn []
      (let [disabled? (fn [marked-tokens] (zero? (count @marked-tokens)))]
        [bs/button
         {:bsStyle "primary"
          :style {:opacity (if (disabled? marked-tokens)  0.65 1)
                  :cursor (if (disabled? marked-tokens) "auto" "pointer")
                  :height "34px"}
          :onClick #(when-not (disabled? marked-tokens)
                      (re-frame/dispatch [:open-modal :annotation-modal]))}
         [:div [:i.zmdi.zmdi-edit]
          [annotation-modal marked-tokens current-ann]]]))))
