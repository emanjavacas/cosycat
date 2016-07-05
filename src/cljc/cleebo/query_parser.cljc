(ns cleebo.query-parser
  (:require [clojure.string :as str]))

(defn re-pos [re s]
  #?(:clj (let [m (re-matcher re s)]
            (if (.find m)
              {:group (.group m)
               :start (.start m)
               :end   (.end m)}))
     :cljs (if-let [m (.exec re s)]
             {:group (first m)
              :start (aget m "index")
              :end   (aget (first m) "length")})))

(defn all-but [s but]
  (str/replace s but ""))

(defn make-pattern [s]
  #?(:clj  (re-pattern s)
     :cljs (js/RegExp s)))

(defn make-start-pattern [qs]
  (let [re-str (str "([" qs "])")]
    (make-pattern re-str)))

(defn make-end-pattern [anchor qs]
  (print qs)
  (let [re-str (str "^[^" (all-but qs anchor) "]+?" anchor)]
    (make-pattern re-str)))

(defn missing-quotes
  "finds missing matching quotes in a FSA fashion"
  [s & {:keys [qs] :or {qs "\"'"}}]
  (letfn [(find-start [s idx]
            (if-let [{group :group start :start end :end :as match}
                     (re-pos (make-start-pattern qs) s)]
              (find-end (subs s (inc start)) (+ idx end) group)
              {:status :finished :at idx}))
          (find-end [s idx anchor]
            (if-let [{group :group start :start end :end :as match}
                     (re-pos (make-end-pattern anchor qs) s)]
              (find-start (subs s end) (+ end idx))
              {:status :mismatch :at idx}))]
    (trampoline find-start s -1)))

