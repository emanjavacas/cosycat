(ns cosycat.routes.session
  (:require [cosycat.routes.utils :refer [safe]]
            [cosycat.components.ws :refer [get-active-users]]
            [cosycat.db.users :refer [user-info users-info user-settings]]
            [cosycat.db.projects :refer [get-projects]]
            [cosycat.app-utils :refer [dekeyword]]
            [config.core :refer [env]]
            [taoensso.timbre :as timbre]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.data.json :as json]))

;;; exceptions
(defn ex-wrong-format []
  (ex-info "Wrong config file" {:error "wrong config file format"}))

(defn ex-not-supported []
  (ex-info "Unsupported format" {:error "Unsupported config format"}))

(defn unknown-corpus-type [server]
  (format "Ignoring entry for server [%s]. Reason: [%s]" server "unknown-corpus-type"))

;;; normalizers
(defn session-tagsets []
  (if-let [dirs (seq (map (partial str "public/") (env :tagset-paths)))]
    (->> (for [dir dirs
               f (->> dir io/resource io/file file-seq)
               :when (and (.isFile f) (.endsWith (.getName f) ".json"))
               :let [public-path (str dir "/" (.getName f))
                     [_ path] (str/split public-path #"public/")]]
           path)
         vec)))

(defn find-corpus-config-format [corpus-config]
  (cond (:endpoints corpus-config) :short
        (:type corpus-config) :full
        :else (throw (ex-wrong-format))))

(defn ->corpora [{:keys [indices]}]
  (reduce-kv
   (fn [l _ {corpus-name :displayName}]
     (conj l {:type :blacklab-server :corpus corpus-name}))
   []
   indices))

(defn ensure-http
  "defaults to http"
  [s & {:keys [protocol] :or {protocol "http"}}]
  (if (.startsWith protocol s) s (str protocol "://" s)))

(defn fetch-all-bl-corpora [server web-service]
  (try
    (let [in (slurp (str (ensure-http server) "/" web-service "?outputformat=json"))]
      (->> in json/read-str clojure.walk/keywordize-keys ->corpora
           (mapv #(assoc % :args {:web-service web-service :server server}))))
    (catch Exception e
      (timbre/info (format "Failed fetching corpus info for server [%s]" server))
      [])))

(defmulti normalize-corpus find-corpus-config-format)

(defmethod normalize-corpus :short
  [{:keys [server endpoints]}]
  (mapcat (fn [{corpus-type :type {corpus :corpus web-service :web-service} :args :as endpoint}]
            (if-not corpus
              (if-not (= corpus-type :blacklab-server)
                (timbre/info (unknown-corpus-type server))
                (fetch-all-bl-corpora server web-service))
              [(assoc-in endpoint [:args :server] server)]))
          endpoints))

(defmethod normalize-corpus :full
  [corpus]
  [corpus])

(defn normalize-corpora [corpora]
  (->> corpora (mapcat normalize-corpus) distinct vec))

(defn session-corpora []
  (if-let [corpora (env :corpora)]
    (normalize-corpora corpora)
    []))

(defn add-active-info [user active-users]
  (if (contains? active-users (:username user))
    (assoc user :active true)
    (assoc user :active false)))

(defn- normalize-users [users username active-users]
  (->> users
       (remove (fn [user] (= username (:username user))))
       (map (fn [user] (dissoc user :settings)))
       (mapv (fn [user] {:username (:username user)
                         :user (add-active-info user active-users)}))))

(defn- get-user-project-settings [user-projects project-name]
  (get-in user-projects [(keyword project-name) :settings]))

(defn- merge-project-settings [projects user-projects]
  (mapv (fn [{:keys [name] :as project}]
          (if-let [user-project-settings (get-user-project-settings user-projects name)]
            (assoc project :settings user-project-settings)
            project))
        projects))

(defn session-users [db username active-users]
  (normalize-users (users-info db) username active-users))

(defn session-projects [db username {user-projects :projects :as me}]
  (-> (get-projects db username) (merge-project-settings user-projects)))

(defn session-settings [{settings :settings :as me}]
  (or settings {}))

(defn session-router
  [{{{username :username roles :roles} :identity} :session
    {db :db ws :ws} :components}]
  (let [active-users (get-active-users ws)
        {settings :settings user-projects :projects :as me} (user-info db username)]
    {:me (dissoc me :settings :projects)
     :users (session-users db username active-users)
     :projects (session-projects db username me)
     :settings (session-settings me)
     :tagsets (session-tagsets)
     :corpora (session-corpora)}))

(def session-route
  (safe (fn [req] {:status 200 :body (session-router req)}) {:login-uri "/login"}))
