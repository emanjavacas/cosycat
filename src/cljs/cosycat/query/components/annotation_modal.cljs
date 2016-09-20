(ns cosycat.query.components.annotation-modal
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [cosycat.utils :refer [by-id parse-annotation nbsp format]]
            [cosycat.app-utils :refer [dekeyword]]
            [cosycat.roles :refer [check-annotation-role]]
            [cosycat.components :refer [disabled-button-tooltip]]
            [cosycat.autosuggest :refer [suggest-annotations]]
            [schema.core :as s]
            [react-bootstrap.components :as bs]
            [taoensso.timbre :as timbre]))

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

(defn dispatch-annotations [ann-map tokens]
  (re-frame/dispatch
   [:dispatch-annotation
    ann-map                    ;ann-map
    (->> tokens (map :hit-id))    ;hit-ids
    (->> tokens (map :id))])) ;token-ids

(defn update-annotations [{:keys [key value]} tokens]
  (doseq [{hit-id :hit-id {ann key} :anns} tokens
          :let [{:keys [_id _version]} ann]]
    (re-frame/dispatch
     [:update-annotation
      {:update-map {:_id _id :_version _version :value value :hit-id hit-id}}])))

(defn deselect-tokens [tokens]
  (doseq [{:keys [hit-id id]} tokens]
    (re-frame/dispatch
     [:unmark-token
      {:hit-id hit-id
       :token-id id}])))

(defn suggest-annotation [new-val existin-annotation]
  (re-frame/dispatch [:notify {:message "To be implemented"}]))

(defn trigger-dispatch
  [action {:keys [value marked-tokens annotation-modal-show current-ann me my-role]}]
  (if-let [[key val] (parse-annotation @value)]
    (let [{:keys [empty-annotation existing-annotation-owner existing-annotation]}
          (group-tokens @marked-tokens @current-ann @me)]
      (cond (not (check-annotation-role action @my-role)) ;user is not authorized
            (notify-not-authorized action @my-role)
            (and (empty? empty-annotation) ;user suggestion to change
                 (empty? existing-annotation-owner)
                 (not (empty? existing-annotation)))
            (suggest-annotation val existing-annotation)
            :else (let [key-val {:key key :value val}] ;dispatch annotations
                    (dispatch-annotations key-val empty-annotation)
                    (update-annotations key-val existing-annotation-owner)
                    (deselect-tokens empty-annotation)
                    (deselect-tokens existing-annotation-owner)))
      (swap! annotation-modal-show not))))

(defn wrap-key [key-code f]
  (fn [e] (when (= key-code (.-charCode e)) (f))))

(defn update-current-ann [current-ann value]
  (fn [target]
    (let [input-data @value
          [_ key] (re-find #"([^=]+)=?" input-data)]
      (reset! current-ann key))))

(defn count-selected [marked-tokens current me]
  (->> @marked-tokens
       (sort-by (juxt :word (fn [{:keys [anns]}] (classify-annotation anns @current @me))))
       (map (juxt :word (fn [token] (get-in token [:anns @current]))))
       frequencies))

(defn background-color [ann me]
  (let [danger  "#fbeded", success "#def0de"]
    (cond
      (not ann) "white"
      (= (:username ann) me) success
      :else danger)))

(defn on-key-press
  [{:keys [value marked-tokens current-ann me my-role annotation-modal-show] :as opts}]
  (wrap-key 13 (fn [] (trigger-dispatch :write opts))))

(defn annotation-input [marked-tokens opts]
  (let [tagsets (re-frame/subscribe [:tagsets])
        value (reagent/atom "")]
    (fn [marked-tokens {:keys [annotation-modal-show current-ann me my-role]}]
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
                :onKeyPress (on-key-press
                             {:value value :marked-tokens marked-tokens
                              :current-ann current-ann :me me :my-role my-role
                              :annotation-modal-show annotation-modal-show})}]]]]])))

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

(defn token-counts-row [[word ann :as token] cnt me]
  (fn [[word ann] cnt me]
    [:tr
     {:style {:background-color (background-color ann @me)}}
     [:td {:style {:padding-bottom "5px"}} word]
     [:td {:style {:padding-bottom "5px"}} [existing-annotation-label ann @me]]
     [:td {:style {:padding-bottom "5px" :text-align "right"}}
      [bs/label {:style {:vertical-align "-30%" :display "inline-block" :font-size "100%"}}
       cnt]]]))

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

(defn annotation-modal [annotation-modal-show marked-tokens current-ann]
  (let [me (re-frame/subscribe [:me :username])
        my-role (re-frame/subscribe [:active-project-role])]
    (fn [annotation-modal-show marked-tokens current-ann]
      [bs/modal
       {:class "large"
        :show @annotation-modal-show
        :on-hide #(do (reset! current-ann "") (swap! annotation-modal-show not))}
       [bs/modal-header
        {:closeButton true}
        [bs/modal-title
         {:style {:font-size "18px"}}
         "Tokens marked for annotation"]]
       [bs/modal-body
        [:div.container-fluid
         [:div.row
          [annotation-input marked-tokens
           {:annotation-modal-show annotation-modal-show
            :me me
            :my-role my-role
            :current-ann current-ann}]
          [:hr]
          [token-counts-table marked-tokens {:current-ann current-ann :me me}]]]]
       [bs/modal-footer
        [bs/button
         {:className "pull-right"
          :bsStyle "info"
          :onClick
          #(trigger-dispatch
            :write
            {:marked-tokens marked-tokens
             :current-ann current-ann
             :me me
             :my-role my-role
             :annotation-modal-show annotation-modal-show})}
         "Submit"]]])))

(defn annotation-modal-button []
  (let [marked-tokens (re-frame/subscribe [:marked-tokens])
        current-ann (reagent/atom "")
        show? (reagent/atom false)]
    (fn []
      (let [disabled? (fn [marked-tokens] (zero? (count @marked-tokens)))]
        [bs/button
         {:bsStyle "primary"
          :style {:opacity (if (disabled? marked-tokens)  0.65 1)
                  :cursor (if (disabled? marked-tokens) "auto" "pointer")
                  :height "34px"}
          :onClick #(when-not (disabled? marked-tokens)
                      (do (reset! current-ann "")
                          (swap! show? not)))}
         [:div [:i.zmdi.zmdi-edit]
          [annotation-modal show? marked-tokens current-ann]]]))))
