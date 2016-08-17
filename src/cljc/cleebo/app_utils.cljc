(ns cleebo.app-utils
  #?(:clj (:require [clojure.test])
     :cljs (:require [goog.dom.pattern :as gpattern]
                     [clojure.set]
                     [goog.string :as gstring])))

;;; math
(defn ceil [n]
  #?(:clj (Math/ceil n) :cljs (.ceil js/Math n)))

;;; syntax
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

(defn dissoc-in
  "Dissociates an entry from a nested associative structure returning a new
  nested structure. keys is a sequence of keys. Any empty maps that result
  will not be present in the new structure."
  [m [k & ks :as keys]]
  (if ks
    (if-let [nextmap (get m k)]
      (let [newmap (dissoc-in nextmap ks)]
        (if (seq newmap)
          (assoc m k newmap)
          (dissoc m k)))
      m)
    (dissoc m k)))

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

(defn update-coll
  "updates a coll at the index where `pred` returns true.
  Coerces output to vector if input coll was a vec. Update is done
  by applying `f` over the matching element and any optionals `args`"
  [coll pred f & args]
  (let [s (for [item coll]
            (if (pred item)
              (apply f item args)
              item))]
    (if (vector? coll) (vec s) s)))

(defn atom? [o] (instance? #?(:clj clojure.lang.Atom :cljs cljs.core/Atom) o))

(defn function? [o] #?(:clj clojure.test/function? :cljs (= (type inc) (type o))))

(defn dekeyword [k] (apply str (rest (str k))))

;;; logic
(defn invalid-project-name [s]
  (and #?(:clj  (re-find #"[ ^\W+]" s)
          :cljs (gpattern/matchStringOrRegex (js/RegExp "[ ^\\W+]") s))
       (= s "_vcs")))

(defn server-project-name [s] (str "_" s))

(defn pending-users [{:keys [updates users] :as project}]
  (let [non-app (->> users (filter #(= "guest" (:role %))) (map :username))
        rest-users (remove (apply hash-set non-app) (map :username users))
        agreed-users (filter (apply hash-set rest-users)
                             (->> updates (filter #(= "delete-project-agree" (:type %))) (map :username)))
        pending (remove (apply hash-set agreed-users) rest-users)]
    {:non-app non-app :agreed-users (vec (apply hash-set agreed-users)) :pending pending}))
