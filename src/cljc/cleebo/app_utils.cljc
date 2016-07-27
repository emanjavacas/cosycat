(ns cleebo.app-utils
  #?(:cljs (:require [goog.dom.pattern :as gpattern])))

;;; SYNTAX
(defn deep-merge
   "Recursively merges maps. If keys are not maps, the last value wins."
   [& vals]
   (if (every? map? vals)
     (apply merge-with deep-merge vals)
     (last vals)))

(defn deep-merge-with
  "Like merge-with, but merges maps recursively, applying the given fn
  only when there's a non-map at a particular level."
  [f & maps]
  (apply
    (fn m [& maps]
      (if (every? map? maps)
        (apply merge-with m maps)
        (apply f maps)))
    maps))

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

(defn flip
  "removes the interesection of S1S2 from the union S1S2"
  [set1 set2]
  (-> set1
      (clojure.set/union set2)
      (clojure.set/difference (clojure.set/intersection set1 set2))))

(defn disjconj
  "applies `disj` or `conj` on a set an given `args` depending on
  whether the elments are already contained or not"
  [s1 & args]
  (reduce #(if (%1 %2) (disj %1 %2) (conj %1 %2)) s1 args))

(defn update-coll [coll pred f & args]
  (let [s (for [item coll]
            (if (pred item)
              (apply f item args)
              item))]
    (if (vector? coll) (vec s) s)))

(defn atom? [o] (instance? #?(:clj clojure.lang.Atom :cljs cljs.core/Atom) o))

;;; LOGIC
(defn invalid-project-name [s]
  #?(:clj  (re-find #"[ ^\W+]" s)
     :cljs (gpattern/matchStringOrRegex (js/RegExp "[ ^\\W+]") s)))
