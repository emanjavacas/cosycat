(ns cleebo.components
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [cleebo.utils :refer [css-transition-group color-codes]]
            [cleebo.localstorage :as ls]
            [taoensso.timbre :as timbre]
            [react-bootstrap.components :as bs]))

(defn error-panel [& {:keys [status status-content]}]
  {:pre [(and status)]}
  [:div.container-fluid.text-center
   {:style {:padding "40px"}}
   [:div.row [:h3 [:span.text-muted status]]]
   [:div.row [:br]]
   [:div.row.text-center status-content]])

(defn dropdown-select [{:keys [label model options select-fn header]}]
  (let [local-label (reagent/atom model)]
    (fn [{:keys [label model options select-fn header] :or {select-fn identity}}]
      [bs/dropdown
       {:id "Dropdown"
        :onSelect (fn [e k] (reset! local-label k) (select-fn k))}
       [bs/button
        {:style {:pointer-events "none !important"}}
        [:span.text-muted label] @local-label]
       [bs/dropdown-toggle]
       [bs/dropdown-menu
        (concat
         [^{:key "header"} [bs/menu-item {:header true} header]
          ^{:key "divider"} [bs/menu-item {:divider true}]]
         (for [{:keys [key label]} options]
           ^{:key key} [bs/menu-item {:eventKey label} label]))]])))

(defn notification-child [msg date by status]
  [:div.container-fluid
   {:style {:padding "0 20px"}}
   [:div.row
    {:style {:padding "0px"
             :padding-bottom "20px"
             :margin "0px"
             :font-weight "bold"}}
    msg [:span.label
         {:style {:margin-left "10px"
                  :font-size "20px"
                  :color (color-codes status)}}
         [:i.zmdi
          {:style {:line-height 1.4}
           :class (case status
                    :info  "zmdi-info"
                    :ok    "zmdi-storage"
                    :error "zmdi-alert-circle")}]]]
   (when date
     [:div.row.pull-right
      {:style {:padding-bottom "10px"}}
      (.toLocaleString date "en-US")])
   (when by [:div.row.pull-right
             {:style {:padding-bottom "10px"}}
             by])])

(defn notification
  [{id :id {msg :msg date :date by :by status :status} :data}]
  ^{:key id}
  [:li#notification
   {:on-click #(re-frame/dispatch [:drop-notification id])}
   [notification-child msg date by (or status :info)]])

(defn notification-container [notifications]
  [:ul#notifications
   [css-transition-group
    {:transition-name "notification"
     :transition-enter-timeout 5000
     :transition-leave-timeout 5000}
    (map (fn [{:keys [id data] :as payload}]
           (notification {:id id :data data}))
         @notifications)]])

(defn load-from-ls-modal [open?]
  (fn [open?]
    [bs/modal
     {:show @open? :on-hide #(swap! open? not)}
     [bs/modal-header
      [bs/modal-title
       [:div "Watch out!" [:span.pull-right [:i.zmdi.zmdi-storage]]]]]
     [bs/modal-body
      [:p "Cleebo found unsaved activities in your browser."]
      [:p "Do you want to restore it? Select 'yes' or 'no'"]
      [:br]
      [:p.text-muted "Note that you might not be able to restore it later"]]
     [bs/modal-footer
      [bs/button-toolbar
       {:className "pull-right"}
       [bs/button
        {:on-click #(let [dump (ls/recover-db)]
                      (re-frame/dispatch [:load-db dump])
                      (re-frame/dispatch [:close-init-modal]))}
        "yes"]
       [bs/button
        {:on-click #(re-frame/dispatch [:close-init-modal])}
        "no"]]]]))
