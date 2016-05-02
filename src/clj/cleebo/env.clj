(ns cleebo.env
  (:require [environ.core :refer [env]]
            [cleebo.components.db :refer [new-db clear-dbs colls]]
            [cleebo.utils :refer [delete-directory]]))

(defn clean-env-no-prompt []
  (let [root (clojure.java.io/file (:dynamic-resource-path env))
        db (.start (new-db (:database-url env)))]
    (do (println "Cleaning app-resources") (delete-directory root))
    (clear-dbs db)
    (.stop db)))

