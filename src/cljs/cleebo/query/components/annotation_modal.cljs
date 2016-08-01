(ns cleebo.query.components.annotation-modal
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [cleebo.utils :refer [by-id ->int parse-annotation filter-dummy-tokens nbsp]]
            [cleebo.components :refer [disabled-button-tooltip]]
            [cleebo.autocomplete :refer [autocomplete-jq]]
            [schema.core :as s]
            [react-bootstrap.components :as bs]))

(defn dispatch-annotations [ann-map marked-tokens]
  (let [filtered-tokens (-> @marked-tokens filter-dummy-tokens)
                                        ;discard those with given ann for given key
        token-ids (map :id filtered-tokens)
        hit-ids (map :hit-id filtered-tokens)]
    ;; for tokens with existing ann, trigger update (if old ann author is client, or check rights) 
    (re-frame/dispatch
     [:dispatch-annotation
      ann-map                    ;ann-map
      hit-ids                    ;hit-ids
      (mapv ->int token-ids)]))) ;token-ids

(defn deselect-tokens [marked-tokens]
  (doseq [{:keys [hit-id id]} @marked-tokens]
    (re-frame/dispatch
     [:unmark-token
      {:hit-id hit-id
       :token-id id}])))

(defn trigger-dispatch
  [{:keys [marked-tokens deselect-on-close annotation-modal-show current-ann me]}]
  (fn [target]
    (when (= 13 (.-charCode target))
      (if-let [[key value] (parse-annotation (by-id "token-ann-key"))]
        (do (when @deselect-on-close (deselect-tokens marked-tokens))
            (swap! annotation-modal-show not)
            (dispatch-annotations {:key key :value value} marked-tokens))))))

(defn update-current-ann [current-ann]
  (fn [target]
    (let [input-data (by-id "token-ann-key")
          [key] (re-find #"([^=]+)=?" input-data)]
      (reset! current-ann key))))

(defn annotation-input [marked-tokens opts]
  (fn [marked-tokens {:keys [deselect-on-close annotation-modal-show current-ann me]}]
    [:table
     {:width "100%"}
     [:tbody
      [:tr
       [:td [:i.zmdi.zmdi-edit]]
       [:td
        [autocomplete-jq
         {:source :complex-source
          :class "form-control form-control-no-border"
          :id "token-ann-key"
          :on-change (update-current-ann current-ann)
          :on-key-press
          (trigger-dispatch
           {:marked-tokens marked-tokens
            :deselect-on-close  deselect-on-close
            :current-ann current-ann
            :me me
            :annotation-modal-show annotation-modal-show})}]]]]]))

(defn count-selected [marked-tokens current-ann]
  (frequencies (map (juxt :word #(get-in % [:anns @current-ann])) @marked-tokens)))

(defn background-color [ann me]
  (let [danger  "#eca9a7", success "#addbad"]
    (cond
      (not ann) "white"
      (= (:username ann) me) success
      :else danger)))

(defn token-row [[word ann] c me]
  (fn [[word ann] c me]
    [:tr
     {:style {:background-color (background-color ann me)}}
     [:td word]
     [:td (when ann (str (get-in ann [:ann :value]) " by: " (:username ann)))]
     [:td {:style {:text-align "right"}}
      [bs/label c]]]))

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
      (for [[[word ann :as token] c] (count-selected marked-tokens current-ann)]
        ^{:key (str word (:username ann) "pop")}
        [token-row token c me])]]))

(defn annotation-modal [annotation-modal-show marked-tokens]
  (let [me (re-frame/subscribe [:me :username])
        deselect-on-close (reagent/atom true)
        current-ann (reagent/atom "")]
    (fn [annotation-modal-show marked-tokens]
      [bs/modal
       {:class "large"
        :show @annotation-modal-show
        :on-hide #(swap! annotation-modal-show not)}
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
           {:deselect-on-close deselect-on-close
            :annotation-modal-show annotation-modal-show
            :me @me
            :current-ann current-ann}]
          [:hr]
          ^{:key "cnt-table"} [token-counts-table marked-tokens {:current-ann current-ann :me @me}]]]]
       [bs/modal-footer
        [:div.container-fluid.pull-left
         {:style {:line-height "40px !important"}}
         [:span.pull-left
          [bs/input
           {:type "checkbox"
            :checked @deselect-on-close
            :onClick #(swap! deselect-on-close not)}]]
         "Deselect tokens after submit?"]
        [bs/button
         {:className "pull-right"
          :bsStyle "info"
          :onClick (trigger-dispatch
                    {:marked-tokens marked-tokens
                     :deselect-on-close  deselect-on-close
                     :current-ann current-ann
                     :me @me
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
