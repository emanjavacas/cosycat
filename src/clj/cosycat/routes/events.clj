(ns cosycat.routes.events
  (:require [compojure.core :refer [routes context POST GET]]
            [cosycat.routes.utils :refer [make-default-route]]
            [cosycat.db.projects :as proj]
            [cosycat.db.users :as users]))

(defn insert-user-project-event-handler
  [{{project :project event :event} :params
    {db :db} :components
    {{username :username} :identity} :session}]
  (users/register-user-project-event db username project event))

(defn user-project-events-handler
  [{{project :project from :from} :params
    {db :db} :components
    {{username :username} :identity} :session}]
  (users/user-project-events db username project :from from))

(defn events-routes []
  (routes
   (context "/events" []
    (POST "/user-project-event" [] (make-default-route insert-user-project-event-handler))
    (GET "/user-project-events" [] (make-default-route user-project-events-handler)))))
