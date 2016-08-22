(ns cleebo.env
  (:require [config.core :refer [env]]
            [monger.collection :as mc]
            [taoensso.timbre :as timbre]
            [cleebo.vcs :as vcs]
            [cleebo.db.projects :as proj]
            [cleebo.components.db :refer [new-db colls]]
            [cleebo.utils :refer [delete-directory]]))

(defn clear-dbs
  [{db :db :as db-conn} & {:keys [collections] :or {collections (keys colls)}}]
  (let [pass (read-line)]
    (if (= pass (:pass env))
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
          (mc/drop db (get colls coll-key)))))))

(defn clean-env-no-prompt []
  (let [root (clojure.java.io/file (:dynamic-resource-path env))
        db (.start (new-db (:database-url env)))]
    (println "Cleaning app-resources")
    (delete-directory root)
    (clear-dbs db)
    (.stop db))) 

