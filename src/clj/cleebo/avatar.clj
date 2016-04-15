(ns cleebo.avatar
  (:require [clojure.java.io :as io])
  (:import [org.hackrslab.avatar RandomAvatarBuilder RandomAvatar RandomAvatar$Extra]))

(defn build-generator ^RandomAvatar []
  (-> (RandomAvatarBuilder.)
      (.squareSize 400)
      (.blockSize 5)
      (.backgroundColor 0x272727)
      (.cache true)
      (.addColor 0 68 204)
      (.addColor 0 136 204)
      (.addColor 81 163 81)
      (.addColor 248 148 6)
      (.addColor 189 54 47)
      (.build)))

(defn- random-avatar
  "Saves a random or seeded png image into filename"
  ([filename] (random-avatar filename "default"))
  ([filename seed]
   (let [generator (build-generator)]
     (.generate generator (io/file filename) (RandomAvatar$Extra/seed seed)))))

(defn new-filename
  [username]
  (let [lcased (.toLowerCase username)]
    (str "public/img/avatars/" lcased ".png")))

(defn new-avatar
  "Creates a new avatar for a user using the `username` as `seed`"
  [username]
  (let [fname (str "resources/" (new-filename username))]
    (random-avatar fname username)
    fname))

(defn get-avatar
  "Tries to read an existing avatar, if it doesn't succeed it creates a new one"
  [username]
  (let [fname (new-filename username)
        resource (io/resource fname)]
    (when-not resource
      (new-avatar username))
    fname))




