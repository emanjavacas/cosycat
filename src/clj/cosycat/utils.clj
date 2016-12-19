(ns cosycat.utils
  (:require [cognitect.transit :as transit]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [schema.core :as s]
            [config.core :refer [env]]))

;;; RESOURCES
(def ^:dynamic *encoding* "UTF-8")

(defn project-version []
  (-> (if (:dev? env)
        "project.clj"        
        (io/resource "project.clj"))
      slurp
      edn/read-string
      (nth 2)))

;;; SYNTAX
(defn read-str
  "Reads a value from a decoded string"
  [^String s type & opts]
  (let [in (java.io.ByteArrayInputStream. (.getBytes s *encoding*))]
    (transit/read (transit/reader in type opts))))

(defn write-str
  "Writes a value to a string."
  [o type & opts]
  (let [out (java.io.ByteArrayOutputStream.)
        writer (transit/writer out type opts)]
    (transit/write writer o)
    (.toString out *encoding*)))

(defn ->int [s]
  (Integer/parseInt s))

(defn ->long [s]
  (Long/parseLong s))

(defn ->keyword [s]
  (keyword (subs s 1)))

(defmacro assert-ex-info
  "Evaluates expr and throws an exception if it does not evaluate to logical true."
  [x & args]
  (when *assert*
    `(when-not ~x
       (throw (ex-info ~@args)))))

(defn new-uuid
  "Computes a random id which can be used as a uuid for a new document"
  []
  (str (java.util.UUID/randomUUID))
  ;; (ObjectId.) ;or just use mongodb's id
  )

;;; ANNS
(defn new-token-id
  "returns new id for a dummy token"
  [idx]
  (str "dummy-" idx))

(defn dummy-hit
  "creates a dummy hit with new id"
  [idx]
  {:id (new-token-id idx) :word ""})

(defn get-token-id
  [token]
  (if-let [id (:id token)]
    (->int id)
    (ex-info "token missing id" token)))

;;; IO
(defn safe-delete [file-path]
  (if (.exists (io/file file-path))
    (try
      (io/delete-file file-path)
      (catch Exception e (str "exception: " (.getMessage e))))
    false))

(defn delete-directory [directory-path]
  (let [directory-contents (file-seq (io/file directory-path))
        files-to-delete (filter #(.isFile %) directory-contents)]
    (doseq [file files-to-delete]
      (safe-delete (.getPath file)))
    (safe-delete directory-path)))

(defn prn-format [s & args]
  (println (apply format s args)))

(defn join-path [dir filename]
  (-> dir
      (-> io/file .getCanonicalPath)
      (io/file filename)
      (.getCanonicalPath)))
