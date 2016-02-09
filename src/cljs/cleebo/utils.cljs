(ns cleebo.utils
  (:require [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [goog.dom.dataset :as gdataset]))

;; (def css-transition-group
;;   (reagent/adapt-react-class js/React.addons.CSSTransitionGroup))

(defn ->map [k l]
  {:key k :label l})

(defn normalize-from [from query-size]
  (max 0 (min from query-size)))

(defn by-id [id & {:keys [value] :or {value true}}]
  (let [elt (.getElementById js/document id)]
    (if value (.-value elt) elt)))

(defn result-by-id [e results-map]
  (let [id (gdataset/get (.-currentTarget e) "id")
        hit (get-in results-map [(js/parseInt id) :hit])]
    id))

(defn time-id []
  (-> (js/Date.)
      (.getTime)
      (.toString 36)))

(defn notify! [{msg :msg}]
  (let [id (time-id)]
    (js/setTimeout #(re-frame/dispatch [:drop-notification id]) 5000)
    (re-frame/dispatch
     [:add-notification
      {:msg msg :id id}])))
