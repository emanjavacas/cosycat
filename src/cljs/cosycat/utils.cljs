(ns cosycat.utils
  (:require [schema.core :as s]
            [cljs.core.async :refer [chan timeout alts! <! close! put!]]
            [goog.dom.dataset :as gdataset]
            [goog.string :as gstr]            
            [cosycat.app-utils :refer [->int]]
            [cosycat.roles :refer [check-annotation-role]]
            [taoensso.timbre :as timbre])
  (:require-macros [cljs.core.async.macros :refer [go-loop]]))

;;; JS-interop
(defn format [fmt & args]
  (apply gstr/format fmt args))

(defn by-id [id & {:keys [value] :or {value true}}]
  (let [elt (.getElementById js/document id)]
    (if value (.-value elt) elt)))

(defn keywordify [m]
  (let [keyword-if-not-int (fn [s] (if (js/isNaN s) (keyword s) (js/parseInt s)))]
    (cond
      (map? m) (into {} (for [[k v] m] [(keyword-if-not-int k) (keywordify v)]))
      (coll? m) (vec (map keywordify m))
      :else m)))

(defn debounce-ch [f millis]
  (let [out (chan), in (chan)]
    (go-loop [last-val nil]
      (let [current (or last-val (<! in))
            timer (timeout millis)
            [new-val ch] (alts! [in timer])]
        (condp = ch
          timer (do (>! out current) (recur nil))
          in (if new-val (recur new-val) (close! out)))))
    (go-loop []
      (let [current (<! out)]
        (f current)
        (recur)))
    in))

(defn debounce [f millis]
  (let [ch (debounce-ch f millis)]
    (fn [arg] (put! ch arg))))

(defn wrap-key [key-code f]
  (fn [e] (when (= key-code (.-charCode e)) (f))))

;;; Time
(defn timestamp []
  (-> (js/Date.)
      (.getTime)))

(defn time-id []
  (-> (timestamp)
      (.toString 36)))

(defn now [] (.now js/Date))

(defn parse-time [i & [opts]]
  (let [js-date (js/Date. i)]
    (if opts
      (.toLocaleDateString js-date "en-GB" (clj->js opts))
      (.toDateString js-date))))

(defn human-time
  [time]
  (parse-time time {"hour" "2-digit" "minute" "2-digit"}))

(defn date-str->locale [date-str]
  (.toLocaleString (js/Date. date-str) "en-GB"))

;;; Component utilities
(defn merge-classes [& classes]
  (->> classes (filter identity) (interpose ".") (apply str)))

(defn nbsp
  "computes a html entity blankspace string of given length"
  [& {:keys [n] :or {n 1}}]
  (apply str (repeat n (gstr/unescapeEntities "&nbsp;"))))

(defn ->map
  "returns a normalized map with named key and label"
  [k l]
  {:key k :label l})

(defn ->default-map
  "index coll as a seq of key-label maps where key equals label"
  [coll]
  (map #(->map % %) coll))

;;; Hit-related
(defn current-results [db]
  (let [active-project (get-in db [:session :active-project])
        project (get-in db [:projects active-project])]
    (get-in project [:session :query :results-summary])))

(defn has-marked?
  "for a given `hit-map` we look if the current marke update leaves marked tokens behind."
  [{:keys [hit meta] :as hit-map} token-id]
  (let [marked-tokens (filter :marked hit)]
    (> (count marked-tokens) 1)))

(defn update-token
  "applies `token-fn` at token with given `token-id`"
  [{:keys [hit meta] :as hit-map} token-id token-fn]
  (assoc hit-map :hit (map (fn [{:keys [id] :as token}]
                             (if (= token-id id)
                               (token-fn token)
                               token))
                           hit)))

(defn filter-marked-hits
  "filter hits according to whether are tick-checked, optionally
  include those containing marked tokens but not tick-cheked"
  [results-by-id & {:keys [has-marked?] :or {has-marked? false}}]
  (into {} (filter
            (fn [[_ {:keys [meta]}]]
              (or (:marked meta) (and has-marked? (:has-marked meta))))
            results-by-id)))

(defn get-token-id
  "returns a token id in integer form defaulting to -1 in case of dummy token"
  [{id :id :as token}]
  (try (js/parseInt id)
       (catch :default e -1)))

;;; Annotations
(defn ->box [color] (str "0 -1.5px " color " inset"))

(defn parse-annotation
  "transforms annotation string into a vec of annotation key and value"
  [s]
  (let [[k v] (gstr/splitLimit s "=" 2)]
    (if (and (not (empty? k)) (not (empty? v)))
      [k v])))

(defn highlight-annotation
  "if a given token has annotations it computes a color for the user with the most
  annotations in that token"
  [{anns :anns :as token} color-map]
  (let [filt-anns (filter #(contains? color-map (:username %)) (vals anns))
        [user _] (first (sort-by second > (frequencies (map :username filt-anns))))]
    (if-let [color (get color-map user)]
      (->box color))))

;;; Resources
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
   :logout "%s is leaving us for a while"
   :new-project "You've been added to project \"%s\" by %s"})

(defn get-msg [path & args]
  (let [fmt (get-in notification-msgs path)]
    (cond (fn? fmt)     (apply format (apply fmt args) args)
          (string? fmt) (apply format fmt args))))
