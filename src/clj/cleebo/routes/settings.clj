(ns cleebo.routes.settings
  (:require [cleebo.routes.utils :refer [safe]]
            [cleebo.db.users :refer [update-user-info]]
            [cleebo.avatar :refer [user-avatar]]
            [cleebo.components.ws :refer [send-clients]]
            [config.core :refer [env]]
            [taoensso.timbre :as timbre]))

(defmulti settings-router (fn [{{route :route} :params}] route))

(defmethod settings-router :new-avatar
  [{{{username :username} :identity} :session
    {db :db ws :ws} :components}]
  (let [avatar (user-avatar username)]
    (update-user-info db username {:avatar avatar})
    (send-clients ws {:type :new-user-avatar :data {:avatar avatar :username username}})
    avatar))

(defmethod settings-router :save-user-settings
  [{{{username :username} :identity} :session
    {db :db ws :ws} :components}]
  ;; todo
  )

(defmethod settings-router :save-project-settings
  [{{{username :username} :identity} :session
    {db :db ws :ws} :components}]
  ;; todo
  )

(def settings-route 
  (safe (fn [req]
          (try {:status 200 :body (settings-router req)}
               (catch Exception e
                 (let [{ex :class} (bean e)]
                   {:status 500 :body {:message "Ooops" :data {:exception (str ex)}}}))))
        {:login-uri "/login"}))
