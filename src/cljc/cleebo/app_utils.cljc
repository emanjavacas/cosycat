(ns cleebo.app-utils
  #?(:cljs (:require [goog.dom.pattern :as gpattern])))

;;; SYNTAX
(defn deep-merge
   "Recursively merges maps. If keys are not maps, the last value wins."
   [& vals]
   (if (every? map? vals)
     (apply merge-with deep-merge vals)
     (last vals)))

(defn select-values [m ks]
  (reduce #(conj %1 (m %2)) [] ks))

(defn map-vals
  "applies a function `f` over the values of a map"
  [f m]
  (into {} (for [[k v] m] [k (f v)])))

(defn transpose
  "groups maps by key using `f` on the coll of vals of each key"
  [f & mlist]
  (into {} (map (fn [[k v]]
                  [k (apply f (map val v))])
                (group-by key (apply concat mlist)))))

(defn default-project-name [username]
  (str username "-playground"))

(defn invalid-project-name [s]
  #?(:clj  (re-find #"[ ^\W+]" s)
     :cljs (gpattern/matchStringOrRegex (js/RegExp "[ ^\\W+]") s)))
