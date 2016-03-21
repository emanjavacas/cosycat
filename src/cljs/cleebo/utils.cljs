(ns cleebo.utils
  (:require [re-frame.core :as re-frame]
            [schema.core :as s]
            [reagent.core :as reagent]
            [cleebo.shared-schemas :refer [annotation-schema]]
            [goog.dom.dataset :as gdataset]
            [goog.string :as gstr]
            [taoensso.timbre :as timbre])
  (:require-macros [cleebo.env :as env :refer [cljs-env]]))

(def corpora
  (let [{cqp-corpora :corpora} (cljs-env :cqp)
        {bl-corpora :corpora}  (cljs-env :blacklab)]
    (concat cqp-corpora bl-corpora)))

(def color-codes
  {:info "#72a0e5"
   :error "#ff0000"
   :ok "#00ff00"})

(def css-transition-group
  (reagent/adapt-react-class js/React.addons.CSSTransitionGroup))

(defn filter-marked-hits
  "filter hits according to whether are tick-checked, optionally
  include those containing marked tokens but not tick-cheked"
  [results-by-id & {:keys [has-marked?] :or {has-marked? false}}]
  (into {} (filter
            (fn [[_ {:keys [meta]}]]
              (or (:marked meta) (and has-marked? (:has-marked meta))))
            results-by-id)))

(defn nbsp [& {:keys [n] :or {n 1}}]
  (apply str (repeat n (gstr/unescapeEntities "&nbsp;"))))

(defn ->map [k l]
  {:key k :label l})

(defn ->int [s]
  (try (js/parseInt s)
       (catch :default e
         s)))

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

(defn date-str->locale [date-str]
  (.toLocaleString (js/Date. date-str) "en-US"))

(defn update-token
  "apply token-fn where due"
  [{:keys [hit meta] :as hit-map} token-id token-fn]
  (assoc
   hit-map
   :hit
   (map (fn [{:keys [id] :as token}]
          (if (= id token-id)
            (token-fn token)
            token))
        hit)))

(s/defn ^:always-validate make-ann :- annotation-schema
  [k v username]
  {:ann {:key k :value v}
   :username username
   :timestamp (.now js/Date)})

(s/defn ^:always-validate make-span-ann  :- annotation-schema
  [k :- s/Str v :- s/Str username :- s/Str IOB :- (s/enum :I :O :B)]
  {:ann {:key k :value {:IOB IOB :value v}}
   :username username
   :timestamp (.now js/Date)})

(s/defn ^:always-validate dispatch-annotation
  [k v hit-id :- s/Int token-id :- s/Int]
  (let [ann (make-ann k v js/username)]
    (re-frame/dispatch
     [:ws :out {:type :annotation
                :status :ok
                :data {:hit-id hit-id
                       :token-id token-id
                       :ann ann}}])))

(s/defn ^:always-validate dispatch-span-annotation 
  [k v hit-id :- s/Int token-ids :- [s/Int]]
  (let [c (dec (count token-ids))]
    (doseq [[idx token-id] (map-indexed vector token-ids)
            :let [IOB (cond (= idx 0) :B
                            (= idx c) :O
                            :else     :I)]]
      (re-frame/dispatch
       [:ws :out {:type :annotation
                  :status :ok
                  :data {:hit-id hit-id
                         :token-id token-id
                         :ann (make-span-ann k v js/username IOB)}}]))))

(defn parse-annotation [s]
  (let [[k v] (gstr/splitLimit s "=" 2)]
    (if (and k v)
      [k v])))
