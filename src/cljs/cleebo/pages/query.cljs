(ns cleebo.pages.query
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [re-com.core :as re-com]
            [cleebo.ws :refer [send-transit-msg!]]
            [ajax.core :refer [GET]]
            [taoensso.timbre :as timbre])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [re-com.core :refer [handler-fn]]))

(defn by-id [id]
  (.getElementById js/document id))

(defn button-tooltip [& {:keys [info md-icon-name size width position]
                         :or {size :smaller width "250px" position :below-left}}]
  {:pre [(and info md-icon-name)]}
  (let [showing? (reagent/atom false)]
    (fn []
      [re-com/popover-tooltip
       :label     info
       :status    :info
       :position  position
       :width     width
       :showing?  showing?
       :on-cancel #(swap! showing? not)
       :anchor    [re-com/md-icon-button
                   :md-icon-name md-icon-name 
                   :size size
                   :on-click #(swap! showing? not)]])))

(defn query [{:keys [query-str type]}])

(defn query-field []
  (let [corpus (re-frame/subscribe [:session :query-opts :corpus])
        size (re-frame/subscribe [:session :query-opts :size])
        context (re-frame/subscribe [:session :query-opts :context])
        asize (reagent/atom @size)]
    (fn []
      [:h2.page-header {:style {:font-weight "5em"}}
       [:div.row
        [:div.col-sm-3 "Query Panel"]
        [:div.col-sm-9
         [:div.form-horizontal
          [:div.form-group.has-feedback
           [:div.input-group
            [:input.form-control
             {:type "text"
              :name "query"
              :id "query-str"
              :placeholder "Example: [pos='.*\\.']"
              :autocorrect "off"
              :autocapitalize "off"
              :spellcheck "false"
              :on-key-press
              #(if (= (.-charCode %) 13)
                 (let [query-str (.-value (by-id "query-str"))]
                   (send-transit-msg!
                    {:msg {:query-str query-str}
                     :type :query
                     :status :ok})))}
             [:i.zmdi.zmdi-search.form-control-feedback
              {:style {:font-size "0.75em" :line-height "35px" :margin-right "35px"}}]]
            [:span.input-group-addon
             [button-tooltip
              :md-icon-name "zmdi-unfold-more"
              :width "300px"
              :info
              [re-com/v-box
               :gap "10px"
               :children
               [[:p.info-subheading "Query advanced options"]
                [re-com/line]
                [re-com/h-box
                 :justify :between
                 :children
                 [[re-com/label :label "corpus"]
                  [re-com/single-dropdown
                   :style {:font-size "11px"}
                   :width "150px"
                   :placeholder "Select a corpus"
                   :choices [{:id "PYCCLE-ECCO"} {:id "PYCCLE-EBBO"} {:id "MBG-CORPUS"}]
                   :label-fn :id
                   :model @corpus
                   :on-change #(.log js/console @corpus)]]]
                [re-com/h-box
                 :justify :between
                 :children
                 [[re-com/label :label "context size"]
                  [re-com/single-dropdown
                   :style {:font-size "11px"}
                   :width "150px"
                   :placeholder "Select a corpus"
                   :choices (map (partial hash-map :id) (range 1 10))
                   :label-fn :id
                   :model @asize
                   :on-change
                   #(do (reset! asize %)
                        (re-frame/dispatch [:set-session [:query-opts :size] %]))]]]]]]]]]]]]])))

(defn field-btn [f field]
  [:button.btn.btn-default.btn-sm 
   {:type "button"
    :class (if (= f @field) "active" "")
    :on-click #(reset! field f)}
   f])

(defn toolbar [field]
  [:div.btn-toolbar
   [:div.btn-group.pull-right
    [:div.btn-group
     [field-btn "word" field]
     [field-btn "pos" field]
     [field-btn "lemma" field]
     [field-btn "reg" field]]
    [:button.btn.btn-primary.btn-sm
     {:type "button" :onClick #(.log js/console "prev!")}
     "Prev!"]
    [:button.btn.btn-primary.btn-sm
     {:type "button" :onClick #(.log js/console "next!")}
     "Next!"]]])

(defn results-frame []
  (let [results (re-frame/subscribe [:query-results])
        field (atom "word")]
    (fn []
      [:div
       [toolbar field]
       [:br]
       [:table.table.table-striped.table-results
        [:thead]
        [:tbody {:style {:font-size "11px"}}
         (for [[i row] (map-indexed vector (first (first @results)))]
           ^{:key i}
           [:tr
            (for [{:keys [pos word id] :as token} row]
              (if (:match token)
                ^{:key (str i "-" id)} [:td.info word]
                ^{:key (str i "-" id)} [:td word]))])]]])))

(defn annotation-frame []
  [:div "annotation frame!!!"])

(defn debug-frame []
  (let [messages (re-frame/subscribe [:msgs])
        results  (re-frame/subscribe [:query-results])]
    (fn []
      [re-com/v-box :children
       [[re-com/h-box :align :center
         :children
         [[re-com/md-icon-button
           :md-icon-name "zmdi-edit"
           :on-click
           #(send-transit-msg! {:status :ok :type :msgs :msg "Hello everyone!"})]
          [re-com/md-icon-button
           :md-icon-name "zmdi-copy"
           :on-click #(timbre/debug "Messages: " @messages)]]]
        [re-com/box
         :child
         [:div
          [:ul (for [[i [msg]] (map-indexed vector (reverse @messages))]
                 ^{:key i} [:li msg])]]]]])))

(defn query-main []
  (let [annotation? (atom true)]
    (fn []
      [re-com/v-box :gap "50px"
       :children 
       [[re-com/box :align :stretch :child [results-frame]]
        (when @annotation?
          [re-com/box :align :center :child [debug-frame]])]])))

(defn query-panel []
  [:div
   [query-field]
   [query-main]])
