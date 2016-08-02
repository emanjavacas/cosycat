(ns cleebo.test.test-config
  (:require [com.stuartsierra.component :as component]
            [monger.collection :as mc]
            [cleebo.components.db :refer [new-db clear-dbs]]
            [config.core :refer [env]]))

(defn assert-db []
  (assert (.endsWith (:database-url env) "cleeboTest") "Wrong DB"))

(defn db-fixture [f]
  (assert-db)
  (def db (component/start (new-db (:database-url env))))
  (clear-dbs db)
  (f)
  (do (clear-dbs db) (component/stop db)))
