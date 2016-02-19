(ns cleebo.utils
  (:require [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [goog.dom.dataset :as gdataset]
            [goog.string :as gstr]
            [schema.coerce :as coerce]
            [cleebo.backend.middleware :refer [db-schema]])
  (:require-macros [cleebo.env :as env :refer [cljs-env]]))

(def corpora
  (let [{cqp-corpora :corpora} (cljs-env :cqp)
        {bl-corpora :corpora}  (cljs-env :blacklab)]
    (concat cqp-corpora bl-corpora)))

(defn filter-marked-hits [results]
  (into {} (filter (fn [[hit-num {:keys [hit meta]}]]
                     (or (:has-marked meta) (:marked meta)))
                   results)))

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

(defn notify! [{msg :msg}]
  (let [id (time-id)]
    (js/setTimeout #(re-frame/dispatch [:drop-notification id]) 5000)
    (re-frame/dispatch
     [:add-notification
      {:msg msg :id id}])))

(defn keyword-if-not-int [s]
  (if (js/isNaN s)
    (keyword s)
    (js/parseInt s)))

(defn keywordify [m]
  (cond
    (map? m) (into {} (for [[k v] m]
                        [(keyword-if-not-int k) (keywordify v)]))
    (coll? m) (vec (map keywordify m))
    :else m))

(defn coerce-json [& {:keys [schema] :or {schema db-schema}}]
  (coerce/coercer schema coerce/json-coercion-matcher))

(defn make-annotation [m]
  (assoc m :username js/username :time (js/Date.)))
