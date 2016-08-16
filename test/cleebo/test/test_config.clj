(ns cleebo.test.test-config
  (:require [com.stuartsierra.component :as component]
            [monger.collection :as mc]
            [cleebo.vcs :as vcs]
            [cleebo.components.db :refer [new-db clear-dbs]]
            [config.core :refer [env]]))

(defn is-test-db? [path]
  (.endsWith path "cleeboTest"))

(defn assert-db [path]
  (assert (not (is-test-db? path)) "Wrong DB"))

(def test-db "mongodb://127.0.0.1:27017/cleeboTest")

(defn db-fixture [f]
  (assert-db (:database-url env))
  (def db (component/start (new-db test-db)))
  (clear-dbs db)
  (vcs/drop-vcs (:db db))
  (f)
  (vcs/drop-vcs (:db db))
  (clear-dbs db)
  (component/stop db))
