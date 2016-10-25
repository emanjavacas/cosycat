(ns cosycat.backend.handlers.events
  (:require [re-frame.core :as re-frame]
            [ajax.core :refer [GET POST]]
            [cosycat.backend.middleware :refer [standard-middleware no-debug-middleware]]
            [taoensso.timbre :as timbre]))

(defmulti insert-events
  (fn [current-events new-events]
    (type new-events)))

(defmethod insert-events cljs.core/PersistentArrayMap
  [events {:keys [id] :as event}] 
  (assoc events id event))

(defmethod insert-events cljs.core/PersistentVector
  [current-events new-events]
  (merge current-events (zipmap (map :id new-events) new-events)))

(defn project-events-handler [project]
  (fn [{:keys [events] :as payload}]
    (re-frame/dispatch [:add-project-event project events])))

(re-frame/register-handler
 :add-project-event
 standard-middleware
 (fn [db [_ project events]]
   (if (get-in db [:projects project])
     (update-in db [:projects project :events] insert-events events)
     db)))

;;; User project events
(re-frame/register-handler
 :register-user-project-event
 (fn [db [_ event]]
   (let [active-project (get-in db [:session :active-project])]
     (POST "/events/user-project-event"
           {:params {:project active-project :event event}
            :handler #(re-frame/dispatch [:add-project-event active-project (:events %)])
            :error-handler #(timbre/info "Error while registering user event")})
     db)))

(re-frame/register-handler
 :fetch-user-project-events
 (fn [db [_ & {:keys [from max-events] :or {max-events 5}}]]
   (if-let [active-project (get-in db [:session :active-project])]
     (GET "/events/user-project-events"
          {:params (cond-> {:project active-project :max-events max-events} from (assoc :from from))
           :handler (project-events-handler active-project)
           :error-handler #(timbre/info "Couldn't fetch user project events")}))
   db))

;;; Project events
(re-frame/register-handler
 :fetch-project-events
 (fn [db [_ project-name & {:keys [from max-events] :or {max-events 5}}]]
   (GET "/events/project-events"
        {:params (cond-> {:project-name project-name :max-events max-events} from (assoc :from from))
         :handler (project-events-handler project-name)
         :error-handler #(timbre/info "Couldn't fetch user project events")})
   db))
