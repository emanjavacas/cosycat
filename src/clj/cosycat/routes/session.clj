(ns cosycat.routes.session
  (:require [cosycat.routes.utils :refer [safe]]
            [cosycat.components.ws :refer [add-active-info]]
            [cosycat.db.users :refer [user-login-info users-public-info user-settings]]
            [cosycat.db.projects :refer [get-projects]]
            [cosycat.db.utils :refer [normalize-user]]
            [cosycat.app-utils :refer [dekeyword normalize-by]]
            [cosycat.utils :refer [join-path]]
            [config.core :refer [env]]
            [taoensso.timbre :as timbre]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.walk :refer [keywordize-keys]]
            [clojure.data.json :as json]))

;;; Exceptions
(defn ex-wrong-format []
  (ex-info "Wrong config file" {:error "wrong config file format"}))

(defn ex-not-supported []
  (ex-info "Unsupported format" {:error "Unsupported config format"}))

(defn unknown-corpus-type [server]
  (format "Ignoring entry for server [%s]. Reason: [%s]" server "unknown-corpus-type"))

;;; Fetch corpus info
(defn find-corpus-config-format [corpus-config]
  (cond (:endpoints corpus-config) :short
        (:type corpus-config) :full
        :else (throw (ex-wrong-format))))

(defn bl-payload->corpora [{:keys [indices]}]
  (reduce-kv
   (fn [l _ {corpus-name :displayName}]
     (conj l {:type :blacklab-server :corpus corpus-name}))
   []
   indices))

(defn ensure-http
  "defaults to http"
  [s & {:keys [protocol] :or {protocol "http"}}]
  (if (.startsWith protocol s) s (str protocol "://" s)))

(defmulti fetch-corpora (fn [endpoint server] (:type endpoint)))

(defmethod fetch-corpora :blacklab-server
  [{corpus-type :type {corpus :corpus web-service :web-service} :args} server]
  (try
    (let [in (slurp (str (ensure-http server) "/" web-service "?outputformat=json"))]
      (->> in json/read-str keywordize-keys bl-payload->corpora
           (mapv #(assoc % :args {:web-service web-service :server server}))))
    (catch Exception e
      (timbre/info (format "Failed fetching corpus info for server [%s]" server))
      [])))

;;; Normalizers
;;; Normalizers: tagsets
(defn session-tagsets [tagset-paths]
  (if-let [dirs tagset-paths]
    (vec (for [dir dirs
               f (->> dir io/file file-seq)
               :when (and (.isFile f) (.endsWith (.getName f) ".json"))
               :let [abspath (join-path dir (.getName f))]]
           (-> abspath slurp json/read-str keywordize-keys)))))

;;; Normalizers: corpora
(defmulti session-corpus find-corpus-config-format)

(defmethod session-corpus :short
  [{:keys [server endpoints]}]
  (mapcat (fn [{{corpus :corpus web-service :web-service} :args :as endpoint}]
            (if-not corpus
              (fetch-corpora endpoint server)
              [(assoc-in endpoint [:args :server] server)]))
          endpoints))

(defmethod session-corpus :full [corpus] [corpus])

(defn session-corpora []
  (or (->> (env :corpora) (mapcat session-corpus) distinct vec) []))

;;; Normalizers: users
(defn session-users [db ws username]
  (let [users (->> (users-public-info db username)
                   ;; remove me to separate user
                   (remove (fn [user] (= username (:username user))))
                   ;; add active info
                   (mapv (fn [user] (add-active-info ws user))))]
    (normalize-by users :username)))

;;; Normalizers: projects
(defn merge-user-projects
  "merge user-specific project info into project"
  [projects user-projects]
  (mapv (fn [{:keys [name] :as project}]
          (let [user-project-settings (get-in user-projects [(keyword name) :settings])]
            (cond-> project
              user-project-settings (assoc :settings user-project-settings))))
        projects))

(defn session-projects [db username {user-projects :projects :as me}]
  (-> (get-projects db username)
      (merge-user-projects user-projects)))

;;; Normalizers: settings
(defn session-settings [me]
  (get me :settings {}))

(defn session-router
  [{{{username :username roles :roles} :identity} :session
    {db :db ws :ws} :components}]
  (let [{settings :settings user-projects :projects :as me} (user-login-info db username)]
    {:me (normalize-user me :settings :projects)
     :users (session-users db ws username)
     :projects (session-projects db username me)
     :settings (session-settings me)
     :tagsets (session-tagsets (env :tagset-paths))
     :corpora (session-corpora)}))

(def session-route
  (safe (fn [req] {:status 200 :body (session-router req)}) {:login-uri "/login"}))
