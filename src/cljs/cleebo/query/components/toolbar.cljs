(ns cleebo.query.components.toolbar
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [taoensso.timbre :as timbre]
            [react-bootstrap.components :as bs]
            [goog.string :as gstr]
            [cleebo.utils :refer [->default-map by-id]]
            [cleebo.components :refer [dropdown-select user-thumb]]
            [cleebo.query.components.annotation-modal
             :refer [annotation-modal-button]]))

(defn pager-button [& {:keys [direction label]}]
  [bs/button
   {:onClick #(re-frame/dispatch [:query-range direction :results-frame])
    :style {:font-size "12px" :height "34px" :width "70px"}}
   label])

(defn pager-buttons []
  [bs/button-toolbar
   {:justified true}
   [pager-button
    :direction :prev
    :label [:div [:i.zmdi.zmdi-arrow-left {:style {:margin-right "10px"}}] "prev"]] 
   [pager-button
    :direction :next
    :label [:div "next" [:i.zmdi.zmdi-arrow-right {:style {:margin-left "10px"}}]]]])

(defn on-click-sort [route]
  (fn []
    (re-frame/dispatch [:query-sort route :results-frame])))

(defn sort-buttons []
  (let [criterion (re-frame/subscribe [:session :query-opts :criterion])
        prop-name (re-frame/subscribe [:session :query-opts :prop-name])
        corpus (re-frame/subscribe [:session :query-opts :corpus])]
    (fn []
      [bs/button-toolbar
       {:justified true}
       [dropdown-select
        {:label "sort by: "
         :header "Select criterion"
         :model @criterion
         :options (->default-map ["match" "left-context" "right-context"])
         :select-fn (fn [k] (re-frame/dispatch [:set-session [:query-opts :criterion] k]))}]
       [dropdown-select
        {:label "sort prop: "
         :header "Select property"
         :options (->default-map ["word" "pos" "lemma"]);[TODO:This is corpus-dependent]
         :model @prop-name
         :select-fn (fn [k] (re-frame/dispatch [:set-session [:query-opts :prop-name] k]))}]
       [bs/button
        {:onClick (on-click-sort :sort-range)}
        "Sort page"]
       [bs/button
        {:onClick (on-click-sort :sort-query)}
        "Sort all"]])))

(defn query-result-label []
  (let [query-results (re-frame/subscribe [:session :query-results])]
    (fn []
      (let [{:keys [from to query-size]} @query-results]
        [:label
         {:style {:line-height "35px"}}
         (let [from (inc from) to (min to query-size)]
           (gstr/format "%d-%d of %d hits" from to query-size))]))))

(defn annotation-hit-button []
  (let [marked-hits (re-frame/subscribe [:marked-hits])]
    (fn []
      [bs/button
       {:bsStyle "primary"
        :style {:visibility (if (zero? (count @marked-hits)) "hidden" "visible")}
        :href "#/annotation"}
       "Annotate"])))

(defn mark-all-hits-btn []
  [bs/button
   {:onClick #(re-frame/dispatch [:mark-all-hits])}
   "Mark all hits"])

(defn demark-all-hits-btn []
  [bs/button
   {:onClick #(re-frame/dispatch [:demark-all-hits])}
   "Demark all hits"])

(defn annotation-buttons []
  [bs/button-toolbar
   [mark-all-hits-btn]
   [demark-all-hits-btn]
   [annotation-modal-button]])

(defn get-all-usernames [project]
  (cons (:creator project) (map :username (:users project))))

(defn filter-annotations-btn []
  (let [active-project (re-frame/subscribe [:active-project])]
    (fn []
      [dropdown-select
       {:label "show annotations by: "
        :header "Pick a user"
        :options (->default-map (cons "all" (get-all-usernames @active-project)))
        :model "a"
        :select-fn (fn [k] "")}])))

(defn filter-user-anns-btn [user filtered & [opts]]
  (fn [{username :username avatar :avatar} filtered]
    [bs/overlay-trigger
     {:overlay (reagent/as-component [bs/tooltip username])
      :placement "bottom"}
     [bs/button
      (merge
       {:active (boolean filtered)
        :onClick #(re-frame/dispatch [:filter-anns-by-user username (not filtered)])}
       opts)
      (reagent/as-component [user-thumb username {:height "25px" :width "25px"}])]]))

(defn display-annotation-buttons []
  (let [filter-user-anns (re-frame/subscribe [:filter-user-anns])
        active-project-users (re-frame/subscribe [:active-project-users])]
    (fn []
      [bs/button-toolbar
       (doall (for [{username :username :as user} @active-project-users
                    :let [filtered (contains? @filter-user-anns username)
                          [r g b] (get @filter-user-anns username)]]
                ^{:key username} [filter-user-anns-btn user filtered
                                  {:style {:background-color (str "rgb(" r "," g "," b ")")}}
                                  ]))])))

(defn toolbar []
  (let [query-size (re-frame/subscribe [:query-results :query-size])]
    (fn []
      [:div.container-fluid
       {:style {:visibility (when (zero? @query-size) "hidden" "visible")}}
       [:div.row {:style {:margin-top "10px"}}
        [:div.col-lg-3.col-sm-3
         [:div.row
          [:div.col-lg-6.col-sm-5.pad [query-result-label]]
          [:div.col-lg-6.col-sm-7.pad [pager-buttons]]]]
        [:div.col-lg-9.col-sm-9.pad
         [:div.pull-right [sort-buttons]]]]
       [:div.row {:style {:margin-top "10px"}}
        [:div.col-lg-8.col-sm-8.pad [annotation-buttons]]
        [:div.col-lg-4.col-sm-4.pad
         [:div.pull-right [display-annotation-buttons]]]]])))
