(ns cleebo.utils
  (:require [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [goog.dom.dataset :as gdataset]
            [goog.string :as gstr]
             [taoensso.timbre :as timbre])
  (:require-macros [cleebo.env :as env :refer [cljs-env]]))

(def corpora
  (let [{cqp-corpora :corpora} (cljs-env :cqp)
        {bl-corpora :corpora}  (cljs-env :blacklab)]
    (concat cqp-corpora bl-corpora)))

(def css-transition-group
  (reagent/adapt-react-class js/React.addons.CSSTransitionGroup))

(defn filter-marked-hits
  [results-by-id]
  (into {} (filter
            (fn [[_ {:keys [meta]}]]
              (:marked meta))
            results-by-id)))

(defn nbsp [& {:keys [n] :or {n 1}}]
  (apply str (repeat n (gstr/unescapeEntities "&nbsp;"))))

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

(defn parse-time [i]
  (let [js-date (js/Date. i)]
    (.toDateString js-date)))

(defn notify! [& {:keys [msg]}]
  {:pre [(and msg)]}
  (let [delay (re-frame/subscribe [:settings :delay])
        id (time-id)]
    (js/setTimeout #(re-frame/dispatch [:drop-notification id]) @delay)
    (re-frame/dispatch [:add-notification {:msg msg :id id}])))

(defn keyword-if-not-int [s]
  (if (js/isNaN s)
    (keyword s)
    (js/parseInt s)))

(defn keywordify [m]
  (cond
    (map? m) (into {} (for [[k v] m] [(keyword-if-not-int k) (keywordify v)]))
    (coll? m) (vec (map keywordify m))
    :else m))

(defn select-values [m ks]
  (reduce #(conj %1 (m %2)) [] ks))
