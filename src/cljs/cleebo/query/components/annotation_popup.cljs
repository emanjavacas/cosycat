(ns cleebo.query.components.annotation-popup
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [cleebo.shared-schemas :refer [make-ann]]
            [cleebo.utils :refer [by-id]]
            [cleebo.ws :refer [send-transit-msg!]]
            [react-bootstrap.components :as bs]))

(defn dispatch-annotations
  [marked-tokens]
  (let [k (by-id "token-ann-key")
        v (by-id "token-ann-val")]
    (doseq [{:keys [hit-id id]} @marked-tokens
            :let [ann (make-ann {k v} js/username)]]
      (send-transit-msg! {:type :annotation :data {:cpos id :ann ann}})
      (re-frame/dispatch
       [:annotate
        {:hit-id hit-id
         :token-id id
         :ann ann}]))))

(defn input-row [marked-tokens]
  (fn [marked-tokens]
    [:tr
     ^{:key "key-input"}
     [:td
      [:input#token-ann-key.form-control
       {:type "text"
        :name "key-input"}]]
     ^{:key "value-input"}
     [:td
      [:input#token-ann-val.form-control
       {:type "text"
        :name "value-input"}]]]))

(defn inner-thead [k1 k2]
  [:thead
   [:tr
    [:th {:style {:padding-bottom "10px" :text-align "left"}}  k1]
    [:th {:style {:padding-bottom "10px" :text-align "right"}} k2]]])

(defn token-annotation-table [marked-tokens]
  (fn [marked-tokens]
    [:table {:width "100%"}
     [:caption [:h4 "Annotation"]]
     (inner-thead "Key" "Value")
     [:tbody
      [input-row marked-tokens]]]))

(defn token-counts-table [marked-tokens]
  (fn [marked-tokens]
    [:table {:width "100%"}
     (inner-thead "Token" "Count")
     [:tbody
      {:style {:font-size "14px !important"}}
      (for [[word c] (frequencies (map :word @marked-tokens))]
        ^{:key (str word "pop")}
        [:tr
         [:td {:style {:padding-bottom "10px" :text-align "left"}} word]
         [:td {:style {:text-align "right"}}
          [bs/label c]]])]]))

(defn deselect-modal [exit-dialog-show marked-tokens]
  (fn [exit-dialog-show marked-tokens]
    [bs/modal
     {:show @exit-dialog-show :on-hide #(swap! exit-dialog-show not)
      :style {:z-index 3000}}
     [bs/modal-header
      [bs/modal-title
       [:div "Deselect?" [:span.pull-right [:i.zmdi.zmdi-check-circle]]]]]
     [bs/modal-body
        [:p "Do you want to drop the selected tokens?"]]
     [bs/modal-footer
      [bs/button-toolbar
       {:className "pull-right"}
       [bs/button
        {:on-click #(do (swap! exit-dialog-show not)
                        (doseq [{:keys [hit-id id]} @marked-tokens]
                          (re-frame/dispatch
                           [:mark-token
                            {:hit-id hit-id
                             :token-id id
                             :flag false}])))}
        "yes"]
       [bs/button
        {:on-click #(swap! exit-dialog-show not)}
        "no"]]]]))

(defn annotation-popup [marked-tokens]
  (let [exit-dialog-show (reagent/atom false)]
    (fn [marked-tokens]
      [bs/overlay-trigger
       {:rootClose true
        :trigger "click"
        :placement "bottom"
        :overlay
        (reagent/as-component
         [bs/popover
          {:style {:min-width "500px"}
           :show true
           :title (reagent/as-component
                   [:span
                    {:style {:font-size "18px"}}
                    "Tokens marked for annotation"])}
          [:div.container-fluid
           [:div.row
            ^{:key "cnt-table"} [token-counts-table marked-tokens]
            [:hr]
            ^{:key "ann-table"} [token-annotation-table marked-tokens]
            [:br]
            [bs/button
             {:className "pull-right"
              :bsStyle "info"
              :onClick #(do (swap! exit-dialog-show not)
                            (dispatch-annotations marked-tokens))}
             "Submit"]]]])}
       [bs/button
        {:bsStyle "info"
         :style {:visibility (if (zero? (count @marked-tokens)) "hidden" "visible")}}
        [:div "Annotate Tokens"
         [deselect-modal exit-dialog-show marked-tokens]]]])))

