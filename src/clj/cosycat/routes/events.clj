(ns cosycat.routes.events
  (:require [compojure.core :refer [routes context POST GET]]
            [cosycat.utils :refer [assert-ex-info]]
            [cosycat.routes.utils :refer [make-default-route]]
            [cosycat.db.projects :as proj]
            [cosycat.db.users :as users]
            [taoensso.timbre :as timbre]))

(defn register-user-project-event-route
  [{{project :project event :event} :params
    {db :db} :components
    {{username :username} :identity} :session}]
  (users/register-user-project-event db username project event)
  (let [last-event (first (users/user-project-events db username project :max-events 1))]
    (assert-ex-info last-event "Error while registering event" {:message "Error while registering event"})
    last-event))

(defn user-project-events-route
  [{{project :project from :from max-events :max-events} :params
    {db :db} :components
    {{username :username} :identity} :session}]
  (users/user-project-events db username project :from from))

(defn events-routes []
  (routes
   (context "/events" []
    (POST "/user-project-event" [] (make-default-route register-user-project-event-route)) 
    (GET "/user-project-events" [] (make-default-route user-project-events-route)))))
