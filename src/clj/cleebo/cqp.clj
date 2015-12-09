(ns cleebo.cqp
  (:require [cqp-clj.spec :refer [read-init]]
            [cqp-clj.core :as cqp]))

(defn create-attr [[type name]] {:attr-type type :attr-name name})

(def attrs (map create-attr [[:pos "word"] [:pos "pos"]]))
(def spec (read-init "dev-resources/cqpserver.init"))

(def result
  (cqp/with-cqi-client [client (cqp/make-cqi-client spec)]
    (cqp/query! client "PYCCLE-ECCO" "'goin.*' @'.*' 'to'" "latin1")
    (cqp/cpos-seq-handler
     client
     "PYCCLE-ECCO"
     (cqp/cpos-range client "PYCCLE-ECCO" 0 10)
     2
     attrs)))
