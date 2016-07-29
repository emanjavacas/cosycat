(ns cleebo.backend.handlers.components
  (:require [re-frame.core :as re-frame]
            [cleebo.backend.middleware :refer [standard-middleware no-debug-middleware]]
            [cleebo.utils :refer [time-id has-marked? update-token]]
            [cleebo.app-utils :refer [deep-merge]]
            [taoensso.timbre :as timbre]))

(re-frame/register-handler
 :open-modal
 standard-middleware
 (fn [db [_ modal & [data]]]
   (if-not data
     (assoc-in db [:session :modals modal] true)     
     (update-in db [:session :modals modal] deep-merge data))))

(re-frame/register-handler
 :close-modal
 standard-middleware
 (fn [db [_ modal]]
   (assoc-in db [:session :modals modal] false)))

(re-frame/register-handler
 :start-throbbing
 standard-middleware
 (fn [db [_ panel]]
   (timbre/debug "start" panel)
   (assoc-in db [:session :throbbing? panel] true)))

(re-frame/register-handler
 :stop-throbbing
 standard-middleware
 (fn [db [_ panel]]
   (timbre/debug "stop" panel)
   (assoc-in db [:session :throbbing? panel] false)))

(re-frame/register-handler
 :register-error
 standard-middleware
 (fn [db [_ component-id data]]
   (assoc-in db [:session :component-error component-id] data)))

(re-frame/register-handler
 :drop-error
 standard-middleware
 (fn [db [_ component-id]]
   (update-in db [:session :component-error] dissoc component-id)))

;;; marking
(re-frame/register-handler
 :mark-hit
 standard-middleware
 (fn [db [_ {:keys [hit-id flag]}]]
   (let [active-project (get-in db [:session :active-project])
         path [:projects active-project :session :query :results-by-id hit-id :meta :marked]]
     (assoc-in db path (boolean flag)))))

(re-frame/register-handler
 :mark-all-hits
 standard-middleware
 (fn [db _]
   (let [active-project (get-in db [:session :active-project])
         path-to-results [:projects active-project :session :query :results-by-id]]
     (reduce (fn [acc hit-id]
               (assoc-in acc (into path-to-results [hit-id :meta :marked]) true))
             db
             (get-in db [:projects active-project :session :query :results])))))

(re-frame/register-handler
 :unmark-all-hits
 standard-middleware
 (fn [db _]
   (let [active-project (get-in db [:session :active-project])
         path-to-results [:projects active-project :session :query :results-by-id]]
     (reduce (fn [acc hit-id]
               (assoc-in acc (into path-to-results [hit-id :meta :marked]) false))
             db
             (keys (get-in db path-to-results))))))



(defn mark-token [token] (assoc token :marked true))

(defn unmark-token [token] (dissoc token :marked))

(re-frame/register-handler
 :mark-token
 standard-middleware
 (fn [db [_ {:keys [hit-id token-id]}]]
   (let [active-project (get-in db [:session :active-project])
         path [:projects active-project :session :query :results-by-id hit-id]
         hit (get-in db path)]
     (assoc-in db path (update-token hit token-id mark-token)))))

(re-frame/register-handler
 :unmark-token
 standard-middleware
 (fn [db [_ {:keys [hit-id token-id]}]]
   (let [active-project (get-in db [:session :active-project])
         hit-map (get-in db [:projects active-project :session :query :results-by-id hit-id])
         hit-map (assoc-in hit-map [:meta :has-marked] (boolean (has-marked? hit-map token-id)))
         path [:projects active-project :session :query :results-by-id hit-id]]
     (assoc-in db path (update-token hit-map token-id unmark-token)))))
