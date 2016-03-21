(ns cleebo.backend.ws-routes
  (:require [re-frame.core :as re-frame]
            [cleebo.utils :refer [update-token make-ann]]
            [cleebo.backend.ws :refer [send-ws]]
            [cleebo.backend.middleware :refer [standard-middleware]]
            [schema.core :as s])
  (:require-macros [cljs.core.match :refer [match]]))

(re-frame/register-handler
 ;; TODO: handle timeouts with channels inside this handler
 :ws
 (fn [db [_ dir {:keys [type status data] :as payload}]]
   (match [dir type status]
     ;; annotation routes
     [:in :annotation :ok]
     (let [{:keys [hit-id token-id anns]} data]
       (re-frame/dispatch
        [:notify
         {:message (str "Stored annotation for token " token-id)
          :status :ok}])
       (re-frame/dispatch
        [:add-annotation
         {:hit-id hit-id
          :token-id token-id
          :anns anns}])
       (update-in db [:throbbing?] dissoc token-id))
     
     [:in :annotation :error]
     (let [{:keys [token-id reason e username]} data]
       (re-frame/dispatch
        [:notify
         {:message (str "Couldn't store annotation for token: " token-id
                        " Reason: " reason)}]
        (update-in db [:throbbing?] dissoc token-id)))
     
     [:out :annotation _]
     (let [{token-id :token-id {timestamp :timestamp} :ann} data]
       (if-let [throbbing? (= timestamp (get-in db [:throbbing? token-id]))]
         (do (re-frame/dispatch
              [:notify
               {:message (str "Processing annotation")
                :status :info}])
             db)
         (do (re-frame/dispatch
              [:notify
               {:message (str "Sent annotation for token " token-id)
                :status :info}])
             (send-ws payload)
             (assoc-in db [:throbbing? token-id] timestamp))))
     ;; notify routes
     [:in :notify _]
     (let [{:keys [by message]} data]
       (do (re-frame/dispatch
            [:notify
             {:message (str by " says " message)
              :by by
              :status status}])
           db)))))

(re-frame/register-handler
 :add-annotation
 standard-middleware
 (fn [db [_ {:keys [hit-id token-id anns]}]]
   (if-let [hit-map (get-in db [:session :results-by-id hit-id])]
     (let [token-fn (fn [token] (assoc token :anns anns))]
       (assoc-in
        db
        [:session :results-by-id hit-id]
        (update-token hit-map (str token-id) token-fn)))
     db)))

