(ns cosycat.review.components.query-toolbar
  (:require [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [react-bootstrap.components :as bs]
            [react-date-range.core :refer [calendar date-range]]
            [cosycat.components :refer [dropdown-select]]
            [cosycat.utils :refer [->map]]
            [cosycat.app-utils :refer [dekeyword disjconj]]
            [taoensso.timbre :as timbre]))

(defn dispatch-query-review []
  (re-frame/dispatch [:query-review]))

(defn on-change-label [path]
  (fn [e]
    (let [new-val (.-value (.-target e))
          path-to-label (into [:review :query-opts :query-map] (conj path :string))]
      (re-frame/dispatch
       [:set-project-session path-to-label new-val]))))

(defn on-change-regex [path as-regex?]
  (fn []
    (let [new-val (not @as-regex?)
          path-to-label (into [:review :query-opts :query-map] (conj path :as-regex?))]
      (re-frame/dispatch
       [:set-project-session path-to-label new-val]))))

(defn text-input [{:keys [path placeholder]}]
  (let [path-to-query-map (into [:project-session :review :query-opts :query-map] path)
        model (re-frame/subscribe (into path-to-query-map [:string]))
        as-regex? (re-frame/subscribe (into path-to-query-map [:as-regex?]))]
    (fn [{:keys [path placeholder]}]
      [:div.form-group
       {:style {:padding "0 5px 0 0"}}
       [:div.input-group
        [:input.form-control
         {:type "text"
          :style {:width "90px"}
          :placeholder placeholder
          :value @model
          :on-change (on-change-label path)
          :on-key-press #(when (and (pos? (count @model)) (= 13 (.-charCode %)))
                           (dispatch-query-review))}]
        [bs/overlay-trigger
         {:overlay (reagent/as-component [bs/tooltip {:id "tooltip"} "Use string as regex?"])}
         [:div.input-group-addon
          [:input {:type "checkbox"
                   :style {:cursor "pointer"}
                   :checked @as-regex?
                   :on-change (on-change-regex path as-regex?)}]]]]])))

(defn select-fn [path]
  (fn [v]
    (re-frame/dispatch [:set-project-session (into [:review :query-opts] path) v])))

(defn context-select []
  (let [context (re-frame/subscribe [:project-session :review :query-opts :context])]
    (fn []
      [dropdown-select
       {:label "context: "
        :header "Select a token context size"
        :style {:padding "0 5px 0 0"}
        :options (map #(->map % %) (range 1 21))
        :model @context
        :select-fn (select-fn [:context])}])))

(defn window-select []
  (let [window (re-frame/subscribe [:project-session :review :query-opts :window])]
    (fn []
      [dropdown-select
       {:label "window: "
        :header "Select a window size around results"
        :options (map #(->map % %) (range 1 21))
        :model @window
        :select-fn (select-fn [:window])}])))

(defn size-select []
  (let [size (re-frame/subscribe [:project-session :review :query-opts :size])]
    (fn []
      [dropdown-select
       {:label "size: "
        :style {:padding "0 5px 0 0"}
        :header "Select number of annotations per page"
        :options (map #(->map % %) [2 3 4 5 7 10 12 15 20 25])
        :model @size
        :select-fn (select-fn [:size])}])))

(defn main-inputs []
  (fn []
    [:form.form-inline
     [text-input {:path [:ann :key] :placeholder "Ann Key"}]
     [text-input {:path [:ann :value] :placeholder "Ann Value"}]
     [text-input {:path [:hit-id] :placeholder "Hit-id"}]
     [context-select]
     [size-select]
     [window-select]]))

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
      [bs/button-group
       [bs/button
        {:onClick #(do (swap! show? not) (reset! target (.-target %)))
         :bsStyle (if has-selection? "primary" "default")}
        label]
       [bs/button
        {:onClick #(on-clear)
         :class "dropdown-toggle"}
        [bs/glyphicon {:glyph "erase"}]]
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

(defn timestamp->moment [timestamp]
  (-> timestamp (js/Date.) (js/moment.)))

(defn today's-ms []
  (let [now (js/Date.)]
    (-> (.getMilliseconds now)
        (+ (* 1000 (.getSeconds now)))
        (+ (* 60000 (.getMinutes now)))
        (+ (* 3600000 (.getHours now))))))

(defn moment->timestamp [moment]
  (-> (.toDate moment) (.getTime) (+ (today's-ms))))

(defn before [date days]
  (js/Date. (.getFullYear date) (.getMonth date) (- (.getDate date) days)))

(defn after [date days]
  (js/Date. (.getFullYear date) (.getMonth date) (+ (.getDate date) days)))

(defn select-range [data]
  (let [{:keys [startDate endDate] :as cljs-data} (js->clj data :keywordize-keys true)
        new-timestamp {:from (moment->timestamp startDate) :to (moment->timestamp endDate)}]
    (re-frame/dispatch
     [:set-project-session [:review :query-opts :query-map :timestamp] new-timestamp])))

(defn time-input-button []
  (let [open? (reagent/atom false), target (reagent/atom nil)
        model (re-frame/subscribe [:project-session :review :query-opts :query-map :timestamp])]
    (fn []
      [bs/button-group
       [bs/button
        {:onClick #(do (swap! open? not) (reset! target (.-target %)))
         :bsStyle (if (empty? @model) "default" "primary")}
        "Time range"]
       [bs/button
        {:onClick #(re-frame/dispatch
                    [:set-project-session [:review :query-opts :query-map :timestamp] {}])
         :class "dropdown-toggle"}
        [bs/glyphicon {:glyph "erase"}]]
       [bs/overlay
        {:show @open?
         :target (fn [] @target)
         :placement "bottom"
         :rootClose true
         :onHide #(swap! open? not)}
        [bs/popover
         {:id "popover"
          :style {:max-width "none"}}
         [date-range
          (let [{:keys [from to] :as model-value} @model
                start (timestamp->moment from)
                end (timestamp->moment to)]
            (cond-> {:onChange select-range
                     :maxDate (js/moment. (js/Date.))}
              (not (empty? model-value)) (assoc :startDate start :endDate end)))]]]])))

(defn rest-inputs []
  (let [users (re-frame/subscribe [:users])
        corpora (re-frame/subscribe [:corpora :corpus])
        user-select (re-frame/subscribe [:project-session :review :query-opts :query-map :username])
        corpus-select (re-frame/subscribe [:project-session :review :query-opts :query-map :corpus])]
    (fn []
      [bs/button-toolbar
       {:class "pull-right"}
       [time-input-button]
       [multiple-select-button
        {:label "Corpora"
         :on-select (on-select-multiple :corpus)
         :on-clear (on-clear-multiple :corpus)
         :title "Select annotation corpus"
         :options (for [corpus @corpora
                        :let [selected? (get-selected :corpus corpus)]]
                    {:key corpus :label corpus :selected? selected?})
         :has-selection? (not (empty? @corpus-select))}]
       [multiple-select-button
        {:label "Users"
         :on-select (on-select-multiple :username)
         :on-clear (on-clear-multiple :username)
         :title "Select annotation authors"
         :options (for [username (map :username @users)
                        :let [selected? (get-selected :username username)]]
                    {:key username :label username :selected? selected?})
         :has-selection? (not (empty? @user-select))}]])))

(defn submit []
  (fn []
    [bs/button-group
     [bs/button
      {:bsStyle "primary"
       :onClick dispatch-query-review}
      "Search"]
     [bs/button
      {:onClick #(re-frame/dispatch [:unset-review-results])}
      [bs/glyphicon {:glyph "erase"}]]]))

(defn query-toolbar []
  (fn []
    [:div.row
     [:div.col-lg-6.col-md-6.text-left [main-inputs]]
     [:div.col-lg-4.col-md-4 [rest-inputs]]
     [:div.col-lg-2.col-md-2.pull-right.text-right [submit]]]))
