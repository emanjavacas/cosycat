(ns cleebo.backend.ws-routes.router
  (:require [re-frame.core :as re-frame]
            [cleebo.ws :refer [send-transit-msg!]]
            [cleebo.backend.middleware
             :refer [standard-middleware no-debug-middleware db-schema]]))

(re-frame/register-handler
 :sent-ws
 standard-middleware
 (fn [db [_ {:keys [type data] :as payload}]]
   (send-transit-msg! payload)
;   (assoc-in db [:requests type] {:status :runnning})
   db))

(declare on-ws-success on-ws-error)
(re-frame/register-handler
 :receive-ws
 (fn [db [_ payload]]
   (let [{:keys [status type data]} payload]
     (cond
       (= status :error) (on-ws-error   db {:type type :data data})
       (= status :ok)    (on-ws-success db {:type type :data data})
       :else             (throw (js/Error. (str "Unknown status: " status)))))))

(defn annotation-route [{:keys [cpos] :as data}]
  (re-frame/dispatch
   [:notify
    {:msg "Hooray! Annotation stored"
     :status :ok}]))

(defn on-ws-success
  [db {:keys [type data] :as payload}]
  (case type
    :annotation (annotation-route data))
  db)

(defn on-ws-error [db {:keys [type data]}]
  (let [msg (case type
              :annotation "Oops! We couldn't store your annotation"
              "Oops there was a dwarfs in the channel")]
    (re-frame/dispatch
     [:notify
      {:msg msg
       :status :error}])
    db))
