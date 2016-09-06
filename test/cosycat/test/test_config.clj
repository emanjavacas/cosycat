(ns cosycat.test.test-config
  (:require [com.stuartsierra.component :as component]
            [monger.collection :as mc]
            [cosycat.vcs :as vcs]
            [cosycat.components.db :refer [new-db]]
            [cosycat.env :refer [clear-dbs]]
            [config.core :refer [env]]))

(defn is-test-db? [path]
  (.endsWith path "cosycatTest"))

(defn assert-db [path]
  (assert (not (is-test-db? path)) "Wrong DB"))

(def test-db "mongodb://127.0.0.1:27017/cosycatTest")

(defn db-fixture [f]
  (assert-db (:database-url env))
  (def db (component/start (new-db test-db)))
  (clear-dbs db)
  (f)
  (clear-dbs db)
  (component/stop db))
