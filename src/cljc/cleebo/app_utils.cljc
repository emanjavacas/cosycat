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

(defn default-project-name [username]
  (str username "-playground"))

(defn invalid-project-name [s]
  #?(:clj  (re-find #"[ ^\W+]" s)
     :cljs (gpattern/matchStringOrRegex (js/RegExp "[ ^\\W+]") s)))
