(ns cleebo.app-utils
  #?(:cljs (:require [goog.dom.pattern :as gpattern])))

(defn default-project-name [username]
  (str username "Playground"))

(defn invalid-project-name [s]
  #?(:clj  (re-find #"[ ^\W+]" s)
     :cljs (gpattern/matchStringOrRegex (js/RegExp "[ ^\\W+]") s)))
