(ns cleebo.routes.projects
  (:require [buddy.auth :refer [authenticated?]]
            [cleebo.routes.auth :refer [safe]]
            [cleebo.db.projects :refer [new-project]]
            [cleebo.components.ws :refer [send-clients]]
            [taoensso.timbre :as timbre]))

(defmulti project-router (fn [{{route :route} :params}] route))
(defmethod project-router :new-project
  [{{route :route project-name :name desc :description users :users} :params
    {{username :username} :identity} :session
    {db :db ws :ws} :components}]
  (let [project (new-project db username project-name desc users)]
    (send-clients
     ws
     {:type :new-project :data project}
     :source-client username :target-clients (map :username users))
    project))
(defmethod project-router :update-project
  [{{route :route name :name desc :description users :users} :params
    {{username :username} :identity} :session
    {db :db ws :ws} :components}]
  (throw (ex-info "not implemented yet")))

(def project-route
  (safe (fn [req]
          (try {:status 200 :body (project-router req)}
               (catch Exception e
                 (let [{message :message ex-class :class} (bean e)]
                   {:status 500
                    :body {:message message
                           :data {:exception (str ex-class) :type :internal-error}}}))
               (catch clojure.lang.ExceptionInfo e
                 (let [{:keys [message data]} (bean e)]
                   {:status 500
                    :body {:message message :data data}}))))
        {:login-uri "/login" :is-ok? authenticated?}))
