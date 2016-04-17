(ns cleebo.env
  (:require [environ.core :as environ]))

(defmacro cljs-env [& ks]
  (get-in environ/env ks))
