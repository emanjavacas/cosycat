(ns cleebo.annotation.page
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [react-bootstrap.components :as bs]
            [goog.string :as gstr]
            [cleebo.shared-schemas :refer [make-ann]]
            [cleebo.utils :refer [parse-time]]
            [cleebo.components :refer [error-panel]]))

(def cell-style
  {:width "80px"
   :padding "0px"
;   :border-sizing "border-box"
   :border "0px solid #eee"
   :margin "5px"})

(defn hit-row [{:keys [hit id meta]}]
  (fn [{:keys [hit id meta]}]
    (into [:tr]
          (for [{:keys [word match anns] :as token} hit
                :let [info (if match "info")]]
            ^{:key (str id "-" (:id token))}
            [:td {:style (assoc cell-style :border-bottom (if anns "4px turquoise solid"))}
             word]))))

(defn parse-annotation [s]
  (let [[k v] (gstr/splitLimit s "=" 2)]
    (make-ann {k v} js/username)))

(defn focus-row [{:keys [hit id meta]} current-token]
  (into [:tr]
        (for [[idx token] (map-indexed vector hit)]
          ^{:key (str "focus-" id "-" (:id token))}
          [:td {:style cell-style}
           [:input.focus-cell
            {:on-key-down
             (fn [pressed]
               (if (= 13 (.-keyCode pressed))
                 (let [ann (parse-annotation (.. pressed -target -value))]
                   (re-frame/dispatch
                    [:annotate
                     {:hit-id id
                      :token-id (:id token)
                      :ann ann}]))))
             :on-focus #(reset! current-token idx)}]])))

(defn annotation-queue [marked-hits current-hit current-token]
  (fn [marked-hits current-hit current-token]
    (let [hit-map (get-in (vec (vals @marked-hits)) [@current-hit])]
      [bs/table
       {;:style {:border-collapse "collapse"}
        :bordered true
        :id "tableAnnotation"}
       [:thead]
       [:tbody {:style {:font-size "14px"}}
        ^{:key (:id hit-map)} [hit-row hit-map]
        ^{:key (str "focus-" (:id hit-map))} [focus-row hit-map current-token]]])))

(defn back-to-query-button []
  [bs/button {:href "#/query"}
   [:span {:style {:padding-right "10px"}}
    [:i.zmdi.zmdi-city-alt]]
   "Back to query"])

(defn token-control-buttons
  []
  [:div.container-fluid
   [:div.row
    [bs/button-toolbar
     {:className "pull-right"}
     [bs/button-group
      [bs/button [bs/glyphicon {:glyph "fast-backward"}]]
      [bs/button [bs/glyphicon {:glyph "backward"}]]
      [bs/button [bs/glyphicon {:glyph "forward"}]]
      [bs/button [bs/glyphicon {:glyph "fast-forward"}]]]]]])

(defn inner-thead [k1 k2]
  [:thead
   [:tr
    [:th {:style {:padding-bottom "10px" :text-align "left"}}  k1]
    [:th {:style {:padding-bottom "10px" :text-align "right"}} k2]]])

(defn control-panel [marked-hits current-hit current-token]
  (fn [marked-hits current-hit current-token]
    (let [hit (get-in (vec (vals @marked-hits)) [@current-hit :hit])
          {:keys [word anns id]} (get (vec hit) @current-token)
          style {:padding-top "5px" :padding-bottom "5px"}]
      [bs/panel
       {:header (reagent/as-component [:h2.text-muted "Control Panel"])
        :footer (reagent/as-component [token-control-buttons])}
       [:div.container-fluid
        [:div.row
         {:style {:padding-bottom "15px"}}
         [:label.pull-right
          (str "Annotating token(s) from hit " @current-hit)]]
        [:div.row
         [:div.col-lg-12.pad 
          [bs/panel
           {:className "text-center"}
           word]]]
        [:div.row
         [bs/table
          {:style {:font-size "14px"}}
          [:thead
           [:tr
            [:th
             {:style {:text-align "left"}}
             [:label "key"]]
            [:th
             {:style {:text-align "right"}}
             [:label "value"]]]]
          [:tbody
           (if anns
             (for [{:keys [ann username timestamp]} (seq anns)
                   :let [[k] (keys ann)
                         [v] (vals ann)]]
               ^{:key (str id "-anns-" k)}
               [:tr {:style {:font-size "16px"}}
                [:td {:style (merge style {:text-align "left"})} [bs/label (str k)]]
                [:td {:style (merge style {:text-align "right"})} [bs/label (str v)]]]))]]]]])))

(defn annotation-panel []
  (let [marked-hits (re-frame/subscribe [:marked-hits])
        current-hit (re-frame/subscribe [:current-annotation-hit])
        current-token (reagent/atom 0)]
    (fn []
      [:div.container-fluid
       {:style {:width "100%" :padding "0 10px 0 10px"}}
       (if (zero? (count @marked-hits))
         [error-panel
          :status "No hits marked for annotation..."
          :status-content [back-to-query-button]]
         [:div.row
          [:div.col-lg-8 [annotation-queue marked-hits current-hit current-token]]
          [:div.col-lg-4 [control-panel marked-hits current-hit current-token]]])])))
