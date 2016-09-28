(ns cosycat.avatar
  (:require [clojure.java.io :as io]
            [gravatar.core :as gr]
            [clj-http.client :as http]
            [cosycat.schemas.user-schemas :refer [avatar-schema]]
            [config.core :refer [env]]
            [taoensso.timbre :as timbre])
     (:import [org.hackrslab.avatar RandomAvatarBuilder RandomAvatar RandomAvatar$Extra]
              [javax.imageio ImageIO ImageReader]
              [javax.imageio.stream ImageInputStream]))

(defn dynamic-abspath [relpath]
  (str (:dynamic-resource-path env) relpath))

(defn find-ext [content-type]
  (-> content-type (clojure.string/split #"/") last))

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
  "computes name for a new random avatar"
  [username & {:keys [relpath] :or {relpath (:avatar-path env)}}]
  (str relpath username (rand-int 100000) ".png"))

(defn new-gravatar-filename
  [username ext & {:keys [relpath] :or {relpath (:avatar-path env)}}]
  (str relpath username ".gravatar." ext))

(defn gravatar
  "tries to get user avatar, returns url if successful and nil otherwise"
  [username email]
  (let [url (gr/avatar-url email :default "404")
        user-agent "Mozilla/5.0 (Windows NT 6.1;) Gecko/20100101 Firefox/13.0.1"]
    (try
      (let [{:keys [body headers]} (http/get url {:headers {"User-Agent" user-agent} :as :byte-array})
            filename (new-gravatar-filename username (find-ext (get headers "Content-Type")))
            abspath (dynamic-abspath filename)]
        (with-open [w (java.io.BufferedOutputStream. (java.io.FileOutputStream. abspath))]
          (.write w body))
        filename)
      (catch clojure.lang.ExceptionInfo e
        (if-let [status (get (ex-data e) :status)]
          (case status
            404 (do (timbre/info (format "Couldn't find gravatar for email [%s]" email)))
            (timbre/info (format "Unrecognized error code [%d]" status))))))))

(defn new-avatar
  "Creates a new avatar for a user, returning the relative path to the created resource"
  [username]
  (let [relpath (new-filename username)
        _ (-> relpath dynamic-abspath random-avatar)]
    relpath))

;;; find major color in image
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
  (-> (io/file f) ImageIO/read get-colors get-brightest color->hex))

(defn find-avatars
  [username & {:keys [relpath] :or {relpath (:avatar-path env)}}]
  (->> relpath
       dynamic-abspath
       io/file 
       file-seq
       (filter #(.startsWith (.getName %) username))))

;;; public api
(defn user-avatar [username email]
  (when-let [fnames (seq (find-avatars username))]
    (dorun (map io/delete-file fnames)))
  (let [relpath (or (gravatar username email) (new-avatar username))
        color (get-hex-color (dynamic-abspath relpath))]
    {:href relpath :dominant-color color}))
