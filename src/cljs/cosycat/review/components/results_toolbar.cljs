(ns cosycat.review.components.results-toolbar
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [react-bootstrap.components :as bs]))

(defn label []
  (let [path-to-summary [:project-session :review :results :results-summary]
        page-size (re-frame/subscribe (into path-to-summary [:page :page-size]))
        page-num (re-frame/subscribe (into path-to-summary [:page :page-num]))
        selected-page-size (re-frame/subscribe (conj path-to-summary :size))
        query-size (re-frame/subscribe (conj path-to-summary :query-size))]
    (fn []
      (let [from (* @selected-page-size @page-num)
            to (+ from @page-size)]
        [:label
         {:style {:line-height "35px"}}
         "Showing " [:strong (min (inc from) to)] "-" [:strong to]
         " of " [:strong @query-size] " annotations"]))))

(defn pager []
  (let [path-to-summary [:project-session :review :results :results-summary]
        query-size (re-frame/subscribe (into path-to-summary [:query-size]))
        page-num (re-frame/subscribe (into path-to-summary [:page :page-num]))
        selected-page-size (re-frame/subscribe (into path-to-summary [:size]))]
    (fn []
      [bs/pagination
       {:style {:margin "0px"}
        :next true
        :prev true
        :first true
        :last true
        :ellipsis false
        :boundaryLinks true
        :items (.ceil js/Math (/ @query-size @selected-page-size))
        :maxButtons 10
        :activePage (inc @page-num)
        :onSelect  #(this-as this
                      (re-frame/dispatch
                       [:query-review
                        {:page-num (dec (.-eventKey this))}]))}])))

(defn results-toolbar []
  (let []
    (fn []
      [:div.row
       [:div.col-lg-6 [label]]
       [:div.col-lg-6.text-right [pager]]])))
