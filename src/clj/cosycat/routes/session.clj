(ns cosycat.routes.session
  (:require [cosycat.routes.utils :refer [safe]]
            [cosycat.components.ws :refer [get-active-users]]
            [cosycat.db.users :refer [user-info users-info user-settings]]
            [cosycat.db.projects :refer [get-projects]]
            [cosycat.app-utils :refer [dekeyword]]
            [config.core :refer [env]]
            [taoensso.timbre :as timbre]
            [clojure.string :as str]
            [clojure.java.io :as io]))

(defn add-active-info [user active-users]
  (if (contains? active-users (:username user))
    (assoc user :active true)
    (assoc user :active false)))

(defn normalize-users [users username active-users]
  (->> users
       (remove (fn [user] (= username (:username user))))
       (map (fn [user] (dissoc user :settings)))
       (mapv (fn [user] {:username (:username user)
                         :user (add-active-info user active-users)}))))

(defn read-tagset-files []
  (let [dirs (map (partial str "public/") (env :tagset-paths))]
    (->> (for [dir dirs
               f (->> dir io/resource io/file file-seq)
               :when (and (.isFile f) (.endsWith (.getName f) ".json"))
               :let [public-path (str dir "/" (.getName f))
                     [_ path] (str/split public-path #"public/")]]
           path)
         vec)))

(defn get-user-project-settings [user-projects project-name]
  (get-in user-projects [(keyword project-name) :settings]))

(defn merge-project-settings [projects user-projects]
  (mapv (fn [{:keys [name] :as project}]
          (if-let [user-project-settings (get-user-project-settings user-projects name)]
            (assoc project :settings user-project-settings)
            project))
        projects))

;; (defonce db (.start (cosycat.components.db/new-db (:database-url env))))

;; (merge-project-settings (get-projects db "user") (:projects (user-info db "user")))

(defn fetch-init-session
  [{{{username :username roles :roles} :identity} :session
    {db :db ws :ws} :components}]
  (let [active-users (get-active-users ws)
        {settings :settings user-projects :projects :as me} (user-info db username)]
    {:me (dissoc me :settings :projects)
     :users (normalize-users (users-info db) username active-users)
     :projects (-> (get-projects db username) (merge-project-settings user-projects))
     :settings (or settings {})
     :tagsets (read-tagset-files)
     :corpora (env :corpora)}))

(def session-route
  (safe (fn [req] {:status 200 :body (fetch-init-session req)}) {:login-uri "/login"}))
