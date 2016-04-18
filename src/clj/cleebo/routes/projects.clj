(ns cleebo.routes.projects
  (:require [buddy.auth :refer [authenticated?]]
            [cleebo.routes.auth :refer [safe]]
            [cleebo.db.projects :refer [user-projects new-project]]
            [cleebo.components.ws :refer [notify-clients]]
            [taoensso.timbre :as timbre]))

(defmulti project-router (fn [{{route :route} :params}] route))

(defmethod new-project-route
  :new-project
  [{{route :route project-name :name desc :description users :users} :params
    {{username :username} :identity} :session
    {db :db ws :ws} :components}]
  (if-let [(new-project db username name desc users)]
;    (notify-clients ws )
    ))

(defn new-project-route
  :update-project
  [{{route :route name :name desc :description users :users} :params
    {{username :username} :identity} :session
    {db :db ws :ws} :components}]
   (throw (ex-info "not implemented yet")))

(def project-route
  (safe (fn [req]
          (try {:status 200 :body (new-project-route req)}
               (catch Exception e
                 (let [{:keys [message class]} (bean e)]
                   {:status 500
                    :body {:message message
                           :data {:exception class :type :internal-error}}}))
               (catch clojure.lang.ExceptionInfo e
                 (let [{:keys [message data]} (bean e)]
                   {:status 500
                    :body {:message message :data data}}))))
        {:login-uri "/login" :is-ok? authenticated?}))
