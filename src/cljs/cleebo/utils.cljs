(ns cleebo.utils
  (:require [re-frame.core :as re-frame]
            [schema.core :as s]
            [reagent.core :as reagent]
            [goog.dom.dataset :as gdataset]
            [goog.string :as gstr]
            [taoensso.timbre :as timbre]))

;;; JS-interop
(defn format [fmt & args]
  (apply gstr/format fmt args))

(defn by-id [id & {:keys [value] :or {value true}}]
  (let [elt (.getElementById js/document id)]
    (if value (.-value elt) elt)))

(defn ->int [s]
  (try (js/parseInt s)
       (catch :default e
         s)))

(defn keywordify [m]
  (let [keyword-if-not-int (fn [s] (if (js/isNaN s) (keyword s) (js/parseInt s)))]
    (cond
      (map? m) (into {} (for [[k v] m] [(keyword-if-not-int k) (keywordify v)]))
      (coll? m) (vec (map keywordify m))
      :else m)))

;;; TIME
(defn timestamp []
  (-> (js/Date.)
      (.getTime)))

(defn time-id []
  (-> (timestamp)
      (.toString 36)))

(defn parse-time [i & [opts]]
  (let [js-date (js/Date. i)]
    (if opts
      (.toLocaleDateString js-date "en-GB" (clj->js opts))
      (.toDateString js-date))))

(defn human-time
  [time]
  (parse-time time {"hour" "2-digit" "minute" "2-digit"}))

(defn date-str->locale [date-str]
  (.toLocaleString (js/Date. date-str) "en-US"))

;;; RESOURCES
(def color-codes
  {:info "#72a0e5"
   :error "#ff0000"
   :ok "#00ff00"})

(def notification-msgs
  {:annotation
   {:ok {:me {:token "Stored annotation for token [%d]"
              :IOB "Stored span annotation for range [%d-%d]"
              :mult "Stored %d annotations!"}
         :other {:token "[%s] inserted an annotation for token [%d]"
                 :IOB "[%s] inserted a span annotation for range [%d-%d]"
                 :mult "[%s] inserted %d annotations!"}}
    :error {:token "Couldn't store annotation with id %d. Reason: [%s]"
            :IOB "Couldn't store span annotation for range [%d-%d]. Reason: [%s]"
            :mult   "Couldn't store %d annotations! Reason: [%s]"}}
   :info  "%s says: %s"
   :signup "Hooray! %s has joined the team!"
   :login "%s is ready for science"
   :logout "%s is leaving us..."
   :new-project "You've been added to project [%s] by user [%s]"})

(defn get-msg [path & args]
  (let [fmt (get-in notification-msgs path)]
    (cond (fn? fmt)     (apply format (apply fmt args) args)
          (string? fmt) (apply format fmt args))))


;;; COMPONENT UTILITIES
(defn nbsp [& {:keys [n] :or {n 1}}]
  (apply str (repeat n (gstr/unescapeEntities "&nbsp;"))))

(defn ->map [k l]
  {:key k :label l})

(defn ->default-map [coll]
  (map #(->map % %) coll))

;;; HIT-RELATED
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

;;; ANNOTATIONS
(defn ->box [color] (str "0 -1.5px " color " inset"))

(defn parse-annotation [s]
  (let [[k v] (gstr/splitLimit s "=" 2)]
    (if (and k v)
      [k v])))

(defn highlight-annotation
  "if a given token has annotations it computes a color for the user with the most
  annotations in that token"
  ([token])
  ([{anns :anns :as token} project-name users-map]
   (let [filt-anns (filter #(contains? users-map (:username %)) (vals anns))
         [user _] (first (sort-by second > (frequencies (map :username filt-anns))))]
     (if-let [color (get users-map user)]
       (->box color)))))

;;; ELSE
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
