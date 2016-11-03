(ns cosycat.routes.events
  (:require [compojure.core :refer [routes context POST GET]]
            [cosycat.app-utils :refer [->int]]
            [cosycat.utils :refer [assert-ex-info]]
            [cosycat.routes.utils :refer [make-default-route]]
            [cosycat.db.projects :as proj]
            [cosycat.db.users :as users]
            [taoensso.timbre :as timbre]))

(defn project-events-route
  [{{project-name :project-name from :from max-events :max-events} :params
    {db :db} :components
    {{username :username} :identity} :session}]
  (let [max-events (Integer/parseInt max-events)
        from (when from (Long/parseLong from))
        events (proj/project-events db username project-name :max-events max-events :from from)]
    {:events events}))

(defn register-user-project-event-route
  [{{project :project event :event} :params
    {db :db} :components
    {{username :username} :identity} :session}]
  (users/register-user-project-event db username project event) ;register
  (let [events (first (users/user-project-events db username project :max-events 1))]
    {:events events})) ;return last event

(defn- safe-from
  "compute `from` taking into account project creation date.
  Avoid having to remove user project events on project delete by ignoring them
  after re-creating a once deleted project"
  [db username project-name from]
  (let [{:keys [created]} (proj/get-project db username project-name)
        created (Long/parseLong created)]
    (if from
      (max created (Long/parseLong from))
      created)))

(defn user-project-events-route
  [{{project :project from :from max-events :max-events} :params
    {db :db} :components
    {{username :username} :identity} :session}]
  (let [max-events (Integer/parseInt max-events)
        from (safe-from db username project from)
        events (users/user-project-events db username project :from from :max-events max-events)]
    {:events events}))

(defn events-routes []
  (routes
   (context "/events" []
    (GET "/project-events" [] (make-default-route project-events-route))
    (POST "/user-project-event" [] (make-default-route register-user-project-event-route)) 
    (GET "/user-project-events" [] (make-default-route user-project-events-route)))))
