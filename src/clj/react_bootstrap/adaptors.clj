(ns react-bootstrap.adaptors
  (:require [clojure.string :as str]))

(defn kebab-case [s]
  (let [chunks (str/split s #"(?=[A-Z])")]
    (.toLowerCase (str/join "-" chunks))))

(defmacro bs-components [& components]
  `(do
     ~@(for [component components
             :let [name (symbol (kebab-case component))]]
         `(def ~name (reagent.core/adapt-react-class
                      ~(symbol "js" (str "ReactBootstrap." component)))))))
