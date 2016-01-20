(ns cleebo.query-check
  (:require [clojure.string :as str]))


(defn xor [a b] (and (or a b) (not (and a b))))

(defn tokenize [s]
  (str/split s #"(?<=['\"\]}])[ ]*(?=['\"\[{])"))

(defn string-match [container contained]
  (let [start (.indexOf container contained)]
    (if-not (neg? start)
      [start (+ start (dec (count contained)))]
      [-1 -1])))

(defn trim-end [s]
  (str/replace s #"([\"']])[+?*]$" "$1"))

(def check-fn-map
  (letfn [(missing-quote [s] (let [start (first s) end (last s)]
                               (or (xor (= \' start) (= \' end))
                                   (xor (= \" start) (= \" end)))))
          (missing-quote-inside [s]
            (boolean (re-find #"(?<=[ =]+)([\"'][^\"']+|[^\"']+[\"'])(?=])" s)))
          (missing-bracket [s]
            (xor (= \[ (first s)) (= \] (last s))))
          (infinite-quantifier [s] false)]
    {:missing-quote missing-quote 
     :missing-quote-inside missing-quote-inside
     :missing-bracket missing-bracket
     :infinite-quantifier infinite-quantifier}))

(defn apply-check-fn-map [check-fn-map token from to]
  {[from to]
   {:token token
    :checks (reduce-kv (fn [acc k v] (assoc acc k (v token))) {} check-fn-map)}})

(defn check-query [s check-fn-map]
  (let [tokens (tokenize s)]
    (loop [i 0
           todo tokens
           acc {}]
      (if-let [cur (first todo)]
        (let [[from to] (string-match (subs s i) cur)
              from (+ from i) to (+ to i)
              result (apply-check-fn-map check-fn-map (str/trim cur) from to)]
          (recur to
                 (next todo)
                 (merge acc result)))
        acc))))

(defn parse-checks [checks-map] 
  (map (fn [[[from to] {checks :checks token :token}]]
         (let [fails (filter #(get checks %) (keys checks))]
           (when (not (empty? fails))
             [token [from to] fails])))
       checks-map))

(parse-checks (check-query "\"the " check-fn-map))
