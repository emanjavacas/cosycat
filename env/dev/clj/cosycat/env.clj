(ns cosycat.env)

(defmacro cljs-env [& ks]
  (get-in config.core/env ks))
