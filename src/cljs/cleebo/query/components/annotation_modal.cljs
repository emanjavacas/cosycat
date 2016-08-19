(ns cleebo.query.components.annotation-modal
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [cleebo.utils :refer [by-id ->int parse-annotation filter-dummy-tokens nbsp]]
            [cleebo.components :refer [disabled-button-tooltip]]
            [cleebo.autocomplete :refer [annotation-autocomplete]]
            [schema.core :as s]
            [react-bootstrap.components :as bs]))

(defn classify-annotation
  ([anns ann-key username] (classify-annotation (get anns ann-key) username))
  ([ann username] (if-not ann
                    :empty-annotation
                    (if (= username (:username ann))
                      :exisiting-annotation-owner
                      :existing-annotation))))

(defn group-tokens [tokens ann-key username]
  (->> tokens filter-dummy-tokens (group-by #(classify-annotation (:anns %) ann-key username))))

(defn dispatch-annotations [ann-map tokens]
  (re-frame/dispatch
   [:dispatch-annotation
    ann-map                    ;ann-map
    (->> tokens (map :hit-id))    ;hit-ids
    (->> tokens (map :id) (mapv ->int))])) ;token-ids

(defn update-annotations [ann-key new-value tokens]
  (doseq [{hit-id :hit-id {ann ann-key} :anns} tokens
          :let [{:keys [_id _version]} ann]]
    (re-frame/dispatch
     [:update-annotation
      {:update-map {:_id _id :_version _version :value new-value :hit-id hit-id}}])))

(defn deselect-tokens [tokens]
  (doseq [{:keys [hit-id id]} tokens]
    (re-frame/dispatch
     [:unmark-token
      {:hit-id hit-id
       :token-id id}])))

(defn trigger-dispatch
  [{:keys [marked-tokens annotation-modal-show current-ann me]}]
  (fn [target]
    (when (= 13 (.-charCode target))
      (if-let [[key value] (parse-annotation (by-id "token-ann-key"))]
        (let [{:keys [empty-annotation existing-annotation-owner existing-annotation]}
              (group-tokens @marked-tokens @current-ann @me)]
          (dispatch-annotations {:key key :value value} empty-annotation)
          (deselect-tokens empty-annotation)
          (update-annotations key value existing-annotation-owner)
          (deselect-tokens existing-annotation-owner)
          (swap! annotation-modal-show not))))))

(defn update-current-ann [current-ann]
  (fn [target]
    (let [input-data (by-id "token-ann-key")
          [_ key] (re-find #"([^=]+)=?" input-data)]
      (reset! current-ann key))))

(defn count-selected [marked-tokens current-ann me]
  (->> @marked-tokens
       (sort-by (juxt :word (fn [{:keys [anns]}] (classify-annotation anns @current-ann @me))))
       (map (juxt :word (fn [token] (get-in token [:anns @current-ann]))))
       frequencies))

(defn background-color [ann me]
  (let [danger  "#fbeded", success "#def0de"]
    (cond
      (not ann) "white"
      (= (:username ann) me) success
      :else danger)))

(defn annotation-input [marked-tokens opts]
  (fn [marked-tokens {:keys [annotation-modal-show current-ann me]}]
    [:table
     {:width "100%"}
     [:tbody
      [:tr
       [:td [:i.zmdi.zmdi-edit]]
       [:td
        [annotation-autocomplete
         {:source :complex-source
          :class "form-control form-control-no-border"
          :id "token-ann-key"
          :on-change (update-current-ann current-ann)
          :on-key-press
          (trigger-dispatch
           {:marked-tokens marked-tokens
            :current-ann current-ann
            :me me
            :annotation-modal-show annotation-modal-show})}]]]]]))

(defn existing-annotation-label [{username :username {val :value} :ann :as ann} me]
  (case (classify-annotation ann me)
    :empty-annotation (nbsp)
    :exisiting-annotation-owner val
    :existing-annotation
    [bs/overlay-trigger
     {:overlay (reagent/as-component [bs/tooltip {:id "tooltip"} (str "by: " username)])
      :placement "right"}
     val]))

(defn token-counts-row [[word ann] c me]
  (fn [[word {username :username {val :value} :ann :as ann}] c me]
    [:tr
     {:style {:background-color (background-color ann @me)}}
     [:td {:style {:padding-bottom "5px"}} word]
     [:td {:style {:padding-bottom "5px"}} (existing-annotation-label ann @me)]
     [:td {:style {:padding-bottom "5px" :text-align "right"}}
      [bs/label {:style {:vertical-align "-30%" :display "inline-block" :font-size "100%"}} c]]]))

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
      (for [[[word ann :as token] c] (count-selected marked-tokens current-ann me)]
        ^{:key (str word (:username ann) "pop")}
        [token-counts-row token c me])]]))

(defn annotation-modal [annotation-modal-show marked-tokens]
  (let [me (re-frame/subscribe [:me :username])
        current-ann (reagent/atom "")]
    (fn [annotation-modal-show marked-tokens]
      [bs/modal
       {:class "large"
        :show @annotation-modal-show
        :on-hide #(do (swap! annotation-modal-show not) (reset! current-ann ""))}
       [bs/modal-header
        {:closeButton true}
        [bs/modal-title
         {:style {:font-size "18px"}}
         "Tokens marked for annotation"]]
       [bs/modal-body
        [:div.container-fluid
         [:div.row
          ^{:key "ann-table"}
          [annotation-input marked-tokens
           {:annotation-modal-show annotation-modal-show
            :me me
            :current-ann current-ann}]
          [:hr]
          ^{:key "cnt-table"} [token-counts-table marked-tokens {:current-ann current-ann :me me}]]]]
       [bs/modal-footer
        [bs/button
         {:className "pull-right"
          :bsStyle "info"
          :onClick (trigger-dispatch
                    {:marked-tokens marked-tokens
                     :current-ann current-ann
                     :me me
                     :annotation-modal-show annotation-modal-show})}
         "Submit"]]])))

(defn annotation-modal-button []
  (let [marked-tokens (re-frame/subscribe [:marked-tokens])
        show? (reagent/atom false)]
    (fn []
      (let [disabled? (fn [marked-tokens] (zero? (count @marked-tokens)))]
        [bs/overlay-trigger
         {:overlay (disabled-button-tooltip #(disabled? marked-tokens) "No tokens selected!")
          :placement "bottom"}
         [bs/button
          {:bsStyle "primary"
           :style {:opacity (if (disabled? marked-tokens)  0.65 1)
                   :cursor (if (disabled? marked-tokens) "auto" "auto")
                   :height "34px"}
           :onClick #(when-not (disabled? marked-tokens) (swap! show? not))}
          [:div [:i.zmdi.zmdi-edit]
           [annotation-modal show? marked-tokens]]]]))))
