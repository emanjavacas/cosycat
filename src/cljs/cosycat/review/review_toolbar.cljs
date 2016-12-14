(ns cosycat.review.review-toolbar
  (:require [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [react-bootstrap.components :as bs]
            [cosycat.components :refer [dropdown-select]]
            [cosycat.utils :refer [->map]]
            [cosycat.app-utils :refer [dekeyword disjconj]]
            [taoensso.timbre :as timbre]))

;;; Ann & context
(defn on-input-open [label state-atom open?]
  (fn []
    (let [path [:review-input-open? label]]
      (if-not @open?
        (re-frame/dispatch [:set-project-session-component path true])
        (do (re-frame/dispatch [:set-project-session-component path false])
            (reset! (get state-atom label) nil))))))

(defn text-input [label state-atom]
  (let [open? (re-frame/subscribe [:project-session :components :review-input-open? label])]
    (fn [label state-atom]
      [:div.form-group
       {:style {:padding "0 5px"}}
       [:div.input-group
        [:input.form-control
         {:type "text"
          :style {:width "90px"}
          :disabled (not @open?)
          :placeholder (dekeyword label)
          :value @(get state-atom label)
          :on-change #(reset! (get state-atom label) (.-value (.-target %)))}]
        [:div.input-group-addon
         {:onClick (on-input-open label state-atom open?)
          :style {:cursor "pointer"}}
         [bs/glyphicon
          {:glyph "pencil"}]]]])))

(defn select-fn [path]
  (fn [v]
    (re-frame/dispatch [:set-project-session (into [:review :query-opts] path) v])))

(defn main-inputs []
  (let [context (re-frame/subscribe [:project-session :review :query-opts :context])
        input-state-atom {:key (reagent/atom nil) :value (reagent/atom nil)}]
    (fn []
      [:form.form-inline
       [text-input :key input-state-atom]
       [text-input :value input-state-atom]
       [dropdown-select
        {:label "context: "
         :header "Select a token context size"
         :options (map #(->map % %) (range 20))
         :model @context
         :select-fn (select-fn [:context])}]])))

;;; Username & Corpora
(defn multiple-select-row [{:keys [key label selected?]} on-select]
  (fn [{:keys [key label selected?]} on-select]
    [:li
     [:div.checkbox
      [:label
       [:input
        {:type "checkbox"
         :on-change #(on-select key)
         :checked @selected?}]
       label]]]))

(defn multiple-select-button [{:keys [label on-select on-clear title options has-selection?]}]
  (let [show? (reagent/atom false), target (reagent/atom nil)]
    (fn [{:keys [label on-select on-clear title options has-selection?]}]
      [:div [bs/button-group
             [bs/button
              {:onClick #(do (swap! show? not) (reset! target (.-target %)))
               :bsStyle (if has-selection? "primary" "default")}
              label]
             [bs/button
              {:onClick #(on-clear)}
              "Clear"]]
       [bs/overlay
        {:show @show?
         :target (fn [] @target)
         :placement "bottom"
         :rootClose true
         :onHide #(swap! show? not)}
        [bs/popover
         {:id "popover"
          :title (reagent/as-component
                  [:div.container-fluid.pad
                   [:div.row.pad
                    [:div.col-sm-10.pull-left [:h5 title]]
                    [:div.col-sm-2.text-right
                     {:style {:font-size "12px" :text-align "right"}}
                     [:span
                      {:style {:cursor "pointer" :line-height "3.3"}
                       :onClick #(swap! show? not)}
                      "✕"]]]])}
         [:ul
          {:style {:list-style "none"}}
          (doall
           (for [{:keys [key] :as opts} options]
             ^{:key key} [multiple-select-row opts on-select]))]]]])))

(defn on-select-multiple [label]
  (let [path (conj [:review :query-opts :query-map] label)]
    (fn [key]
      (re-frame/dispatch [:update-project-session path (fn [the-set] (disjconj the-set key))]))))

(defn on-clear-multiple [label]
  (fn []
    (re-frame/dispatch [:set-project-session [:review :query-opts :query-map label] #{}])))

(defn get-selected [label value]
  (re-frame/subscribe [:review-query-opts-selected? label value]))

(defn rest-inputs []
  (let [users (re-frame/subscribe [:users])
        corpora (re-frame/subscribe [:corpora :corpus])
        user-select (re-frame/subscribe [:project-session :review :query-opts :query-map :username])
        corpus-select (re-frame/subscribe [:project-session :review :query-opts :query-map :corpus])]
    (fn []
      [:div.container-fluid
       [:div.row
        [:div.col-lg-6
         [multiple-select-button
          {:label "Corpora"
           :on-select (on-select-multiple :corpus)
           :on-clear (on-clear-multiple :corpus)
           :title "Select annotation corpus"
           :options (for [corpus @corpora
                          :let [selected? (get-selected :corpus corpus)]]
                      {:key corpus :label corpus :selected? selected?})
           :has-selection? (not (empty? @corpus-select))}]]
        [:div.col-lg-6
         [multiple-select-button
          {:label "Users"
           :on-select (on-select-multiple :username)
           :on-clear (on-clear-multiple :username)
           :title "Select annotation authors"
           :options (for [username (map :username @users)
                          :let [selected? (get-selected :username username)]]
                      {:key username :label username :selected? selected?})
           :has-selection? (not (empty? @user-select))}]]]])))

;;; Timestamp
(defn time-inputs []
  [:div "Hi"])

(defn review-toolbar []
  (let []
    (fn []
      [:div.row
       [:div.col-lg-6.col-md-6.text-left [main-inputs]]
       [:div.col-lg-2.col-md-2.text-right [time-inputs]]
       [:div.col-lg-4.col-md-4.text-right [rest-inputs]]])))
