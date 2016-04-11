(ns cleebo.test.db-annotations-test
    (:require [clojure.test :refer [deftest testing is]]
              [com.stuartsierra.component :as component]
              [cleebo.db.annotations
               :refer [new-token-annotation]]
              [cleebo.components.db :refer [new-db]]))

;; (def db (component/start (new-db {:url "mongodb://127.0.0.1:27017/cleeboTest"})))
;; (def annotation-data {:ann {"hey" "ho"}, :username "user", :timestamp 1456308774136})

;; (fetch-annotation db 1)

;; (deftest annotations-db-test
;;   (let [ann (new-token-annotation db 1 annotation-data)]))
