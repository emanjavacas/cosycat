(ns cosycat.routes.admin
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [compojure.core :refer [routes context POST GET]]
            [cosycat.app-utils :refer [->int]]
            [cosycat.routes.auth :refer [is-admin?]]
            [cosycat.routes.utils :refer [make-default-route ex-user check-user-rights]]
            [cosycat.db.projects :as proj]
            [cosycat.components.ws :refer [send-clients send-client]]
            [config.core :refer [env]]
            [taoensso.timbre :as timbre]))

(defn ext [f]
  (let [[f & ext] (clojure.string/split f #"\.")]
    (if (coll? ext)
      (last ext)
      ext)))

(defn log-files
  [log-dir & {:keys [nohidden only-files sort-by-date ext]
              :or {nohidden true only-files true sort-by-date true ext "log"}}]
  (cond->> (io/file log-dir)
    true file-seq
    ext (filter #(.endsWith (.getName %) ext))
    nohidden (remove #(:hidden (bean %)))
    only-files (filter #(:file (bean %)))
    sort-by-date (sort-by #(.lastModified %) >)
    true (map #(.getPath %))))

(defn log-lines [f & {:keys [reverse?] :or {reverse? true}}]
  (let [lines (-> (slurp f) (str/split #"\n(?=[^\s])"))]
    (if reverse?
      (reverse lines)
      lines)))

(defn lazy-log-lines [log-dir]
  (->> (log-files log-dir) (mapcat log-lines)))

(defn lines-in-range [lines-seq from to]
  (let [lines (->> (drop from lines-seq) (take (- to from)))]
    (doall (for [[idx line] (map-indexed vector lines)]
             {:idx (+ from idx) :line line}))))

(defn log-route
  [{{from :from to :to :or {to (+ (->int from) 10)}} :params}]
  {:lines (vec (lines-in-range (lazy-log-lines (:log-dir env)) (->int from) (->int to)))})

(defn remove-project-route
  [{{{username :username} :identity} :session
    {db :db ws :ws} :components
    {project-name :project-name} :params}]
  (if-let [{:keys [users]} (proj/find-project-by-name db project-name)]
    (do (proj/erase-project db project-name)
        (send-clients ws {:type :remove-project :data {:project-name project-name}}
                      :source-client username
                      :target-clients (mapv :username users)))))

(defn admin-routes []
  (routes
   (context "/admin" []
     (GET "/log" [] (make-default-route log-route :is-ok? is-admin?))
     (POST "/remove-project" []
           (make-default-route remove-project-route :is-ok? is-admin?)))))

