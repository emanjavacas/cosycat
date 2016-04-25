(ns cleebo.avatar
  (:require [clojure.java.io :as io]
            [schema.core :as s]
            [cleebo.schemas.app-state-schemas :refer [avatar-schema]])
  (:import [org.hackrslab.avatar RandomAvatarBuilder RandomAvatar RandomAvatar$Extra]
           [javax.imageio ImageIO ImageReader]
           [javax.imageio.stream ImageInputStream]))

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

(defn random-avatar
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
  (let [fname (new-filename username)
        full-fname (str "resources/" fname)]
    (do (random-avatar full-fname username)
        fname)))

(defn get-avatar
  "Tries to read an existing avatar, if it doesn't succeed it creates a new one"
  [username]
  (let [fname (new-filename username)
        resource (io/resource fname)]
    (when-not resource
      (new-avatar username))
    fname))

(defn slurp-pixels [f]
  (ImageIO/read f))

(defn get-rgb-array [pxl]
  (let [alpha (bit-and 0xff (bit-shift-right pxl 24))
        red   (bit-and 0xff (bit-shift-right pxl 16))
        green (bit-and 0xff (bit-shift-right pxl 8))
        blue  (bit-and 0xff pxl)]
    {:red red :green green :blue blue}))

(defn get-colors [img]
  (into #{} (for [i (range 0 (.getHeight img) 50)
                  j (range 0 (.getWidth img) 50)
                  :let [pxl (.getRGB img i j)]]
              (get-rgb-array pxl))))

(defn get-brightest [colors]
  (first (sort-by #(apply + (vals %)) > colors)))

(defn int->hex [n]
  {:post [(= 2 (count %))]}
  (format "%02X" n))

(defn ->hex [{:keys [red green blue]}]
  (str "#" (int->hex red) (int->hex green) (int->hex blue)))

(defn get-hex-color [f]
  (-> (io/resource f)
      slurp-pixels
      get-colors
      get-brightest
      ->hex))

(s/defn ^:always-validate user-avatar [username] :- avatar-schema
  (let [f (new-avatar username)
        c (get-hex-color f)]
    {:href f :dominant-color c}))

