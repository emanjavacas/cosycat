(ns cleebo.layout
  (:require [selmer.parser :as parser]
            [ring.util.response :refer [content-type]]))

(parser/set-resource-path! (clojure.java.io/resource "templates"))
(defn home-page []
  (parser/render-file "index.html" {}))

(defn error-page [error-details]
  (parser/render-file "error.html" error-details)
  ;; (-> {:status (:status error-details)
  ;;      :body (parser/render-file "error.html" error-details)}
  ;;     (content-type "text/html"))
  )
