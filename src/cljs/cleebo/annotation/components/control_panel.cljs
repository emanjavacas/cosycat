(ns cleebo.annotation.components.control-panel
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [react-bootstrap.components :as bs]))

(defn token-control-button
  [glyph current-fn & {:keys [current-hit current-token]}]
  {:pre [(or current-hit current-token)]}
  [bs/button
   {:bsSize "small"
    :on-click #(if current-hit
                 (swap! current-hit current-fn)
                 (swap! current-token current-fn))}
   [bs/glyphicon {:glyph glyph}]])

(defn safe-dec [n] (max 0 (dec n)))
(defn safe-inc [top] (fn [n] (min top (inc n))))

(defn token-control-buttons
  [marked-hits current-hit current-token]
  (fn [marked-hits current-hit current-token]
    (let [top-hit (dec (count @marked-hits))
          top-token (dec (count (:hit (nth @marked-hits @current-hit))))]
      [bs/button-toolbar
       {:className "pull-right"}
       [bs/button-group
        [token-control-button "fast-backward" safe-dec :current-hit current-hit]
        [token-control-button "backward" safe-dec      :current-token current-token]
        [token-control-button "forward" (safe-inc top-token) :current-token current-token]
        [token-control-button "fast-forward" (safe-inc top-hit) :current-hit current-hit]]])))

(defn control-panel-header [marked-hits current-hit current-token]
  (fn [marked-hits current-hit current-token]
    [:h4.text-muted;.pull-right
     {:style {:text-align "right"}}
     [:div.container-fluid
      [:div.row
       (str "Annotating hit #" (inc @current-hit) " of " (count @marked-hits) " marked hits")]]]))

(defn control-panel-current [word]
  (fn [word]
    [bs/panel
     {:className "text-center"}
     word]))

(defn control-panel-anns [marked-hits current-hit current-token]
  (fn [marked-hits current-hit current-token]
    (let [{:keys [hit meta id]} (nth @marked-hits @current-hit)
          {:keys [word anns id]} (nth hit @current-token)]
      (if anns
        [bs/table
         {:style {:font-size "14px"}
          :condensed true}
         [:thead
          [:tr
           [:th.text-center [:label "Annotation type"]]
           [:th.text-center [:label "Value"]]]]
         [:tbody
          (for [{{key :key value :value} :ann
                 username :username
                 timestamp :timestamp} (seq anns)]
            ^{:key (str id "-anns-" key)}
            [:tr {:style {:font-size "16px"}}
             [:td.text-center
              {:style {:padding-top "5px" :padding-bottom "5px"}}
              key]
             [:td.text-center
              {:style {:padding-top "5px" :padding-bottom "5px"}}
              [bs/label (reagent/as-component value)]]])]]))))

;; (defn control-panel [marked-hits current-hit current-token]
;;   (fn [marked-hits current-hit current-token]
;;     (let [{:keys [hit meta id]} (nth @marked-hits @current-hit)
;;           {:keys [word anns id]} (nth hit @current-token)]
;;       [:div.panel.panel-default
;;        {:style {:border-radius "1px"}}
;;        [:div.panel-heading
;;         [control-panel-header marked-hits current-hit current-token]]
;;        [:div.panel-body
;;         [:div.container-fluid
;;          [:div.row [token-control-buttons marked-hits current-hit current-token]]
;;          [:br]
;;          [:div.row [control-panel-current word]]
;;          [:div.row [control-panel-anns marked-hits current-hit current-token]]]]])))

(defn control-panel [marked-hits current-hit current-token]
  (fn [marked-hits current-hit current-token]
    [:div [token-control-buttons marked-hits current-hit current-token]]))
