(ns cosycat.db-utils
  (:require [config.core :refer [env]]
            [monger.collection :as mc]
            [taoensso.timbre :as timbre]
            [cosycat.vcs :as vcs]
            [cosycat.db.projects :as proj]
            [cosycat.components.db :refer [new-db colls]]
            [cosycat.utils :refer [delete-directory]]))

(defn clear-dbs
  [{db :db :as db-conn} & {:keys [collections] :or {collections (keys colls)}}]
  (let [projects (mc/find-maps db (:projects colls))]
    ;; remove projects
    (doseq [{project-name :name} projects]
      (timbre/info (format "Clearing project [%s]" project-name))
      (proj/erase-project db-conn project-name))
    ;; remove vcs coll
    (timbre/info "Clearing vcs collections")
    (vcs/drop-vcs db)
    ;; remove db collections
    (doseq [coll-key collections]
      (timbre/info (format "Clearing collection [%s]" (get colls coll-key)))
      (mc/drop db (get colls coll-key)))))

(defn clean-env-no-prompt []
  (let [root (clojure.java.io/file (:dynamic-resource-path env))
        db (.start (new-db (:database-url env)))]
    (timbre/info "Cleaning app-resources")
    (delete-directory root)
    (clear-dbs db)
    (.stop db))) 

