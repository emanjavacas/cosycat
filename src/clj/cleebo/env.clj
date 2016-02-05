(ns cleebo.env
  (:require [environ.core :refer [env]]))

(defmacro cljs-env [& ks]
  (get-in env ks))
