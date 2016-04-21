(ns cleebo.utils
  (:require [re-frame.core :as re-frame]
            [schema.core :as s]
            [reagent.core :as reagent]
            [goog.dom.dataset :as gdataset]
            [goog.string :as gstr]
            [taoensso.timbre :as timbre]))

(def color-codes
  {:info "#72a0e5"
   :error "#ff0000"
   :ok "#00ff00"})

(def css-transition-group
  (reagent/adapt-react-class js/React.addons.CSSTransitionGroup))

(defn deep-merge
   "Recursively merges maps. If keys are not maps, the last value wins."
   [& vals]
   (if (every? map? vals)
     (apply merge-with deep-merge vals)
     (last vals)))

(defn filter-marked-hits
  "filter hits according to whether are tick-checked, optionally
  include those containing marked tokens but not tick-cheked"
  [results-by-id & {:keys [has-marked?] :or {has-marked? false}}]
  (into {} (filter
            (fn [[_ {:keys [meta]}]]
              (or (:marked meta) (and has-marked? (:has-marked meta))))
            results-by-id)))

(defn filter-dummy-tokens
  [hit]
  (filter #(not (.startsWith (:id %) "dummy")) hit))

(defn nbsp [& {:keys [n] :or {n 1}}]
  (apply str (repeat n (gstr/unescapeEntities "&nbsp;"))))

(defn ->map [k l]
  {:key k :label l})

(defn ->default-map [coll]
  (map #(->map % %) coll))

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

(defn parse-time [i & [opts]]
  (let [js-date (js/Date. i)]
    (if opts
      (.toLocaleDateString js-date "en-GB" (clj->js opts))
      (.toDateString js-date))))

(defn human-time
  [time]
  (parse-time time {"hour" "2-digit" "minute" "2-digit"}))

(defn parse-annotation [s]
  (let [[k v] (gstr/splitLimit s "=" 2)]
    (if (and k v)
      [k v])))

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
  [{:keys [hit meta] :as hit-map} check-token-fn token-fn]
  (assoc hit-map :hit (map (fn [{:keys [id] :as token}]
                             (if (check-token-fn id)
                               (token-fn token)
                               token))
                           hit)))

(defn format [fmt & args]
  (apply gstr/format fmt args))

(defn highlight-annotation
  ([token])
  ([{anns :anns :as token} users-map]
   (let [filtered-anns (filter #(contains? users-map (:username %)) (vals anns))
         [username _] (first (sort-by second > (frequencies (map :username filtered-anns))))]
     (if-let [color (get users-map username)]
       (str "0 -3px " color " inset")))))

(defn dominant-color
  "http://stackoverflow.com/a/2541680"
  [img-href & {:keys [block-size] :or {block-size 5}}]
  (let [img-el (doto (.createElement js/document "img") (.setAttribute "src" img-href))
        canvas (.createElement js/document "canvas")
        context (doto (.getContext canvas "2d") (.drawImage img-el 0 0))
        height (or (.-height canvas) (.-naturalHeight img-el)
                   (.-offsetHeight img-el) (.-height img-el))
        width (or (.-width canvas) (.-naturalWidth img-el)
                  (.-offsetWidth img-el) (.-width img-el))]
    (.drawImage context img-el 0 0)
    (let [data (.getImageData context 0 0 width height)
          length (.-length (.-data data))]
      (loop [i 0 c 0 r 0 g 0 b 0]
        (if (>= i length)
          [(->int (/ r c)) (->int (/ g c)) (->int (/ b c))]
          (recur (+ i (* block-size 4))
                 (inc c)
                 (+ r (aget (.-data data) i))
                 (+ g (aget (.-data data) (inc i)))
                 (+ b (aget (.-data data) (+ 2 i)))))))))
