(ns cleebo.components.ws
  (:require [taoensso.timbre :as timbre]
            [schema.core :as s]
            [com.stuartsierra.component :as component]
            [org.httpkit.server :as kit]
            [clojure.core.async :refer
             [chan go >! <! >!! <!! alts! put! take! timeout close!]]
            [clojure.core.match :refer [match]]
            [cleebo.shared-schemas :refer [ws-from-server]]
            [cleebo.utils :refer [write-str read-str ->int]]
            [cleebo.db.annotations :refer [new-token-annotation]]))

(def messages
  {:shutting-down {:status :ok
                   :type :notify
                   :data {:message "Server is going to sleep!"
                          :by "Server"}}
   :goodbye       {:status :info
                   :type :notify
                   :data {:message "Goodbye world!"
                          :by ""}}
   :hello         {:status :info
                   :type :notify
                   :data {:message "Hello world!"
                          :by ""}}})

(declare notify-client notify-clients ws-routes)

(defn close-chans [ws]
  (let [{:keys [chans]} ws]
    (dorun (map close! (vals chans)))))

(defrecord WS [clients chans db]
  component/Lifecycle
  (start [component]
    (timbre/info "Starting WS component")
    (if (and clients chans)
      component
      (let [chans {:ws-in (chan) :ws-out (chan)}
            component (assoc component :clients (atom {}) :chans chans)]
        (ws-routes component)
        component)))
  (stop [component]
    (timbre/info "Shutting down WS component")
    (if (not (and clients chans))
      component
      (do (notify-clients component (messages :shutting-down))
          (timbre/info "Waiting 5 seconds to notify clients")
          (Thread/sleep 5000)
          (close-chans component)
          (assoc component :clients nil :chans nil)))))

(defn new-ws []
  (map->WS {}))

(defn connect-client [ws ws-ch ws-name]
  (let [{{ws-out :ws-out} :chans clients :clients} ws]
    (timbre/info ws-name "opened ws-channel connection")
    (notify-clients ws (update-in (messages :hello) [:data] assoc :by ws-name))
    (swap! clients assoc ws-name ws-ch)))

(defn disconnect-client [ws ws-name status]
  (let [{{ws-out :ws-out} :chans clients :clients} ws]
    (timbre/info ws-name "closed ws-channel connection with status: " status)
    (notify-clients ws (update-in (messages :goodbye) [:data] assoc :by ws-name))
    (swap! clients dissoc ws-name)))

(defn ws-handler-http-kit [req]
  (let [{{ws :ws} :components
         {{username :username} :identity} :session} req
        {{ws-in :ws-in ws-out :ws-out} :chans clients :clients} ws]
    (kit/with-channel req ws-ch
      (connect-client ws ws-ch username)
      ;; must go somewhere else?
      (go (loop []
            (if-let [{:keys [ws-target ws-from payload]} (<! ws-out)]
              (let [ws-target-ch (get @clients ws-target)]
                (timbre/info "sending" payload)
                (kit/send! ws-target-ch (write-str payload :json))
                (recur)))))
      (kit/on-close ws-ch (fn [status] (disconnect-client ws username status)))
      (kit/on-receive
       ws-ch
       (fn [payload] ;apply route-handler?
         (let [parsed-payload (read-str payload :json)]
           (put! ws-in {:ws-from username :payload parsed-payload})))))))

(defn annotation-route [ws payload]
  (let [{ws-from :ws-from {:keys [type status data]} :payload} payload
        {token-id :token-id hit-id :hit-id ann :ann} data]
    (try
      (let [{:keys [anns]} (new-token-annotation (:db ws) (->int token-id) ann)
            payload {:data {:token-id token-id :hit-id hit-id :anns anns}
                     :status :ok :type :annotation}]
        ;; eventually notify other clients of the new annotation
        {:ws-target ws-from :ws-from ws-from :payload payload})
      (catch Exception e
        (let [payload {:status :error :type :annotation
                       :data {:token-id token-id
                              :reason :internal-error
                              :e (str e)}}]
          {:ws-target ws-from :ws-from ws-from :payload payload})))))

(defn notify-route [ws payload]
  (let [{ws-from :ws-from payload :payload} payload]
    {:ws-target ws-from :ws-from ws-from :payload {:type :notify :data {}}}))

(defn ws-routes [ws]
  (let [{{ws-in :ws-in ws-out :ws-out} :chans} ws]
    (go (loop []
          (if-let [payload (<! ws-in)]
            (let [{ws-from :ws-from {type :type} :payload} payload]
              (case type
                :annotation (put! ws-out (annotation-route ws payload))
                :notify     (put! ws-out (notify-route ws payload))
                (timbre/info "Unidentified route:" type))
              (recur)))))))

(defn notify-client
  [ws ws-target payload & {:keys [ws-from] :or {ws-from "Server"}}]
  (let [{{ws-out :ws-out} :chans clients :clients} ws]
    (put! ws-out {:ws-target ws-target :ws-from ws-from :payload payload})))

(defn notify-clients
  [ws payload & {:keys [ws-from] :or {ws-from "Server"}}]
  (let [{{ws-out :ws-out} :chans clients :clients} ws]
    (doseq [[ws-name _] (seq @clients)
            :when (not= ws-from ws-name)]
      (timbre/info "notifying client" ws-name)
      (put! ws-out {:ws-target ws-name :ws-from ws-from :payload payload}))))
