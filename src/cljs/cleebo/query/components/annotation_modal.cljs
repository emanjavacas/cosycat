(ns cleebo.query.components.annotation-modal
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [cleebo.utils :refer [by-id ->int parse-annotation]]
            [cleebo.autocomplete :refer [autocomplete-jq]]
            [schema.core :as s]
            [react-bootstrap.components :as bs]))

(defn dispatch-annotations
  [marked-tokens]
  (if-let [[k v] (-> (by-id "token-ann-key") parse-annotation)]
    (let [filtered-tokens (remove #(-> (:id %) js/parseInt js/isNaN) @marked-tokens)
          token-ids (map :id filtered-tokens)
          hit-ids (map :hit-id filtered-tokens)]
      (re-frame/dispatch
       [:dispatch-annotation
        {:key k :value v}               ;ann-map
        (mapv ->int hit-ids)            ;hit-ids
        (mapv ->int token-ids)]))))     ;token-ids

(defn inner-thead [k1 k2]
  [:thead
   [:tr
    [:th {:style {:padding-bottom "10px" :text-align "left"}}  k1]
    [:th {:style {:padding-bottom "10px" :text-align "right"}} k2]]])

(defn token-annotation-table [marked-tokens]
  (fn [marked-tokens]
    [:table
     {:width "100%"}
     [:tbody
      [:tr
       [:td "Annotation"]
       [:td
        [autocomplete-jq
         {:source :complex-source
          :class "form-control form-control-no-border"
          :id "token-ann-key"}]]]]]))

(defn token-counts-table [marked-tokens]
  (fn [marked-tokens]
    [:table
     {:width "100%"}
     (inner-thead "Token" "Count")
     [:tbody
      {:style {:font-size "14px !important"}}
      (for [[word c] (frequencies (map :word @marked-tokens))]
        ^{:key (str word "pop")}
        [:tr
         [:td {:style {:padding-bottom "10px" :text-align "left"}} word]
         [:td {:style {:text-align "right"}}
          [bs/label c]]])]]))

(defn annotation-modal [annotation-modal-show marked-tokens]
  (let [deselect-on-close (reagent/atom true)]
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
          ^{:key "ann-table"} [token-annotation-table marked-tokens]
          [:hr]
          ^{:key "cnt-table"} [token-counts-table marked-tokens]]]]
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
          :onClick #(do (when @deselect-on-close
                          (doseq [{:keys [hit-id id]} @marked-tokens]
                            (re-frame/dispatch
                             [:mark-token
                              {:hit-id hit-id
                               :token-id id
                               :flag false}])))
                        (swap! annotation-modal-show not)
                        (dispatch-annotations marked-tokens))}
         "Submit"]]])))

(defn on-click-annotation-btn [disabled? annotation-modal-show]
  (fn []
    (when-not (disabled?)
      (swap! annotation-modal-show not))))

(defn disabled-button-tooltip [disabled?]
  (if (disabled?)
    (reagent/as-component
     [bs/tooltip {:id "tooltip"} "No tokens selected!"])
    (reagent/as-component [:span])))

(defn annotation-modal-button []
  (let [marked-tokens (re-frame/subscribe [:marked-tokens])
        show? (reagent/atom false)]
    (fn []
      (let [disabled? (fn [marked-tokens] (zero? (count @marked-tokens)))]
        [bs/overlay-trigger
         {:overlay (disabled-button-tooltip #(disabled? marked-tokens))
          :placement "right"}
         [bs/button
          {:bsStyle "primary"
           :style (when (disabled? marked-tokens) {:opacity 0.65 :cursor "auto"})
           :onClick (on-click-annotation-btn #(disabled? marked-tokens) show?)}
          [:div "Annotate Marked Tokens"
           [annotation-modal show? marked-tokens]]]]))))
