(ns cleebo.utils
  (:require [cognitect.transit :as transit]))

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
