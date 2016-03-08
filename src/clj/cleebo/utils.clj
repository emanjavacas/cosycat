(ns cleebo.utils
  (:require [cognitect.transit :as transit]
            [schema.core :as s]))

(def ^:dynamic *encoding* "UTF-8")

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

(defn ->keyword [s]
  (keyword (subs s 1)))

(defn wrap-safe
  "turns eventual exception into a proper response body"
  [f]
  (fn [& args]
    (try (let [out (apply f args)]
           (assoc out :status {:status :ok :status-content "OK"}))
         (catch Exception e
           {:status {:status :error :status-content (str e)}}))))

(defn new-token-id
  "returns new id for a dummy token"
  [idx]
  (str "dummy-" idx))

(defn dummy-hit
  "creates a dummy hit with new id"
  [idx]
  {:id (new-token-id idx) :word ""})

(s/defn ^:always-validate get-token-id :- s/Int
  [token]
  (if-let [id (:id token)]
    (try
      (->int id)
      (catch NumberFormatException e
        -1))
    (ex-info "token missing id" token)))

