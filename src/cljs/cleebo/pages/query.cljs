(ns cleebo.pages.query
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [re-com.core :as re-com]
            [cleebo.ws :refer [send-transit-msg!]]
            [taoensso.timbre :as timbre])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn by-id [id]
  (.getElementById js/document id))

(defn query-field []
  [:h2.page-header {:style {:font-weight "5em"}}
   [:div.row
    [:div.col-sm-3 "Query Panel"]
    [:div.col-sm-9
     [:div.form-horizontal
      [:div.input-group      
       [:input.form-control
        {:name "query"
         :type "text"
         :id "query"
         :placeholder "Example: [pos='.*\\.']"
         :autocorrect "off"
         :autocapitalize "off"
         :spellcheck "false"}]
       [:span.input-group-addon
        [re-com/md-icon-button
         :md-icon-name "zmdi-search"
         :size :smaller
         :on-click
         #(let [query (.-value (by-id "query"))]
            (send-transit-msg!
             {:msg {:query-str query}
              :type :query
              :status :ok}))]]]]]]])

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
           :on-click #(timbre/debug "Messages: " @results)]]]
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
