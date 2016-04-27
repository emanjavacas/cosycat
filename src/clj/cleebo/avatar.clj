(ns cleebo.avatar
  (:require [clojure.java.io :as io]
            [schema.core :as s]
            [cleebo.schemas.app-state-schemas :refer [avatar-schema]]
            [environ.core :refer [env]]
            [taoensso.timbre :as timbre])
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
      (.addColor 127 127 220)
      (.addColor 100 207 172)
      (.addColor 198 87 181)
      (.addColor 134 166 220)
      (.build)))

(defn random-avatar
  "Saves a random or seeded png image into filename"
  ([filename] (random-avatar filename (str (rand-int 100000))))
  ([filename seed]
   (let [generator (build-generator)]
     (.generate generator (io/file filename) (RandomAvatar$Extra/seed seed)))))

(defn new-filename
  [username & {:keys [rel-path] :or {rel-path "public/img/avatars/"}}]
  (str rel-path username (str (rand-int 100000)) ".png"))

(defn new-avatar
  "Creates a new avatar for a user"
  [username]
  (let [rel-path (new-filename username)
        abs-path (str (:resource-path env) rel-path)]
    (do (random-avatar abs-path)
        rel-path)))

(defn get-avatar
  "Tries to read an existing avatar, if it doesn't succeed it creates a new one"
  [username]
  (let [rel-path (new-filename username)
        resource (io/resource rel-path)]
    (when-not resource
      (new-avatar username))
    rel-path))

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
  (format "%02X" n))

(defn color->hex [{:keys [red green blue]}]
  (str "#" (int->hex red) (int->hex green) (int->hex blue)))

(defn get-hex-color [f]
  (-> (io/resource f)
      slurp-pixels
      get-colors
      get-brightest
      color->hex))

(defn find-avatars
  [username & {:keys [rel-path] :or {rel-path "public/img/avatars/"}}]
  (->> (io/resource rel-path)
       io/file
       file-seq
       (filter #(.startsWith (.getName %) username))))

(defn user-avatar [username]
  (when-let [fnames (seq (find-avatars username))]
    (dorun (map io/delete-file fnames)))
  (let [rel-path (new-avatar username)
        color (get-hex-color rel-path)]
    {:href rel-path :dominant-color color}))

