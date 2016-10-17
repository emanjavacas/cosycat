(ns cosycat.backend.handlers.events
  (:require [re-frame.core :as re-frame]
            [ajax.core :refer [GET POST]]
            [cosycat.backend.middleware :refer [standard-middleware no-debug-middleware]]
            [taoensso.timbre :as timbre]))

(defmulti insert-events (fn [current-events new-events] (type new-events)))

(defmethod insert-events cljs.core/PersistentArrayMap
  [events event] 
  (into (or events []) [event]))

(defmethod insert-events cljs.core/PersistentVector
  [current-events new-events]
  (into (or current-events []) new-events))

(re-frame/register-handler
 :add-user-project-event
 standard-middleware
 (fn [db [_ project event]]
   (if-let [project-data (get-in db [:projects project])]
     (assoc-in db [:projects project :events] (insert-events (:events project-data) event))
     (do (timbre/info "Attempt to insert user-project-event to missing project " project)
         db))))

(re-frame/register-handler
 :register-user-project-event
 (fn [db [_ event]]
   (let [active-project (get-in db [:session :active-project])]
     (POST "/events/user-project-event"
           {:params {:project active-project :event event}
            :handler #(timbre/info "Succesfully registered user event")
            :error-handler #(timbre/error "Error while registering user event")})
     (re-frame/dispatch [:add-user-project-event active-project event])
     db)))

(re-frame/register-handler
 :fetch-user-project-events
 (fn [db [_ & {:keys [from]}]]
   (let [active-project (get-in db [:session :active-project])]
     (GET "/events/user-project-events"
          {:params {:project active-project}
           :handler #(re-frame/dispatch [:add-user-project-event active-project %])
           :error-handler #(timbre/error "Couldn't fetch user project events")})
     db)))
