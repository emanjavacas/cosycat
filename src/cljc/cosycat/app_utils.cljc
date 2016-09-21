(ns cosycat.app-utils
  #?(:clj (:require [clojure.test])
     :cljs (:require [goog.dom.pattern :as gpattern]
                     [clojure.set]
                     [goog.string :as gstring])))

;;; math
(defn ceil [n]
  #?(:clj (Math/ceil n) :cljs (.ceil js/Math n)))

(defn ->int [s]
  #?(:cljs (js/parseInt s)
     :clj  (Integer/parseInt s)))

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

(defn is-last-partition
  "given a partitioned collection of len `length` compute whether 
  partition at idx `partition-idx` is the last if the collection 
  was partitioned in `partition-size` partitions"
  [length partition-size partition-idx]
  (>= (* partition-size (inc partition-idx)) length))

(defn atom? [o] (instance? #?(:clj clojure.lang.Atom :cljs cljs.core/Atom) o))

(defn function? [o] #?(:clj clojure.test/function? :cljs (= (type inc) (type o))))

(defn dekeyword [k] (apply str (rest (str k))))

;;; logic
(defn invalid-project-name [s]
  (or #?(:clj  (re-find #"[ ^\W+]" s)
         :cljs (gpattern/matchStringOrRegex (js/RegExp "[ ^\\W+]") s))
      (= s "_vcs")))

(defn server-project-name [s] (str "_" s))

(defn pending-users [{:keys [updates users] :as project}]
  (let [non-app (->> users (filter #(= "guest" (:role %))) (map :username))
        rest-users (remove (apply hash-set non-app) (map :username users))
        agreed-users (filter (apply hash-set rest-users)
                             (->> updates (filter #(= "delete-project-agree" (:type %)))
                                  (map :username)))
        pending (remove (apply hash-set agreed-users) rest-users)]
    {:non-app non-app :agreed-users (vec (apply hash-set agreed-users)) :pending pending}))

;;; parse token id
(defn -parse-token
  "parses token id and returns a map with token id metadata"
  [token-id & {:keys [format] :or {format :simple}}]
  (case format
    :complex (let [[doc id] #?(:clj  (clojure.string/split token-id #"\.")
                                :cljs (.split token-id #"\."))]
                {:doc doc :id (->int id)})
    :simple {:id (->int token-id)}))

(defn includes? [s substr]
  #?(:clj (.contains s substr)
     :cljs (not= -1 (.indexOf s substr))))

(defn parse-token [token-id]
  (cond (integer? token-id) (-parse-token token-id)
        (and (string? token-id) (includes? token-id ".")) (-parse-token token-id :format :complex)
        :else (let [msg (str "Unknown token-id format: " token-id)]
                (throw #?(:clj (ex-info msg {:token-id token-id})
                          :cljs (js/Error. msg))))))

(defn token-id->span
  ([token-id]
   (let [{doc :doc scope :id} (parse-token token-id)]
     (if doc
       {:type "token" :scope scope :doc doc}
       {:type "token" :scope scope})))
  ([token-from token-to]
   (let [{doc-from :doc scope-from :id} (parse-token token-from)
         {doc-to :doc scope-to :id} (parse-token token-to)]
     (if (and doc-from doc-to)
       (do (assert (= doc-from doc-to) "Annotation spans over document end")
           {:type "IOB" :scope {:B scope-from :O scope-to} :doc doc-from})
       {:type "IOB" :scope {:B scope-from :O scope-to}}))))

(defn span->token-id
  "computes token-id(s) from a given span, returns a range in case of IOB span"
  [{type :type doc :doc {B :B O :O :as scope} :scope}]
  (case [type (nil? doc)]
    ["token" true] scope
    ["token" false] (str doc "." scope)
    ["IOB" true] (vec (range B (inc O)))
    ["IOB" false] (mapv #(str doc "." %) (range B (inc O)))))

(defn token-id->int
  "compute a unique integer identifier from str token ids (e.g. BlackLab token ids \"0.123\")
   in order to be able to do integer-based queries (>=, <, etc.). We increase doc number to avoid
   dropping doc number in case it is 0"
  [token-id]
  (if (integer? token-id)
    token-id
    (let [[doc id] (clojure.string/split "0.123" #"\.")]
      (-> (str (inc (->int doc)) id) ->int))))
