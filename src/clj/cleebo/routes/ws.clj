(ns cleebo.routes.ws
  (:require [taoensso.timbre :as timbre]
            [schema.core :as s]
            [cleebo.shared-schemas :refer [ws-from-server]]
            [cleebo.utils :refer [write-str read-str]]
            [cleebo.db.annotations :refer [new-token-annotation]]
            [cleebo.cqp :refer [cqi-query cqi-query-range]]
            [com.stuartsierra.component :as component]
            [org.httpkit.server :as kit]))

(defrecord WS [ws-channels]
  component/Lifecycle
  (start [component]
    (if ws-channels
      component
      (assoc component :ws-channels (atom {}))))
  (stop [component]
    (assoc component :ws-channels nil)))

(defn new-ws []
  (map->WS {}))

(declare notify-clients)
(defn connect!
  [ws-ch ws-channels ws-name]
  (timbre/info ws-name "opened channel connection")
  (notify-clients {:message "Hello world!"} ws-channels ws-ch)
  (swap! ws-channels assoc ws-ch ws-name))

(defn disconnect!
  [ws-ch ws-channels status]
  (let [ws-user (get @ws-channels ws-ch "unknown")]
    (timbre/info ws-user "closed the channel with status:" status)
    (notify-clients {:message "Goodbye world!"} ws-channels ws-ch)
    (swap! ws-channels dissoc ws-ch)))

(declare annotation-route notify-route send->client)
(defn on-ws-success [ws-from ws-channels payload components]
  (let [{:keys [type data]} payload]
    (case type
      :annotation (annotation-route data components ws-from)
      :notify (notify-route data components ws-from))))

(defn on-ws-error [ws-from ws-from-name {:keys [data type]}]
  (let [payload {:type type :status :error :data data}]
    (send->client
     :ws-to ws-from
     :payload payload
     :ws-to-name ws-from-name)))

(defn ws-router
  [ws-from payload components]
  (let [{:keys [type data] :as parsed-payload} (read-str payload :json)
        ws-channels (get-in components [:ws :ws-channels])
        ws-from-name (get @ws-channels ws-from)]
    (assert ws-from-name "Got message from not registered channel")
    (timbre/debug "Received " parsed-payload "from" ws-from-name)
    (try
      (on-ws-success ws-from ws-channels parsed-payload components)
      (catch Exception e                ;on-error
        (on-ws-error ws-from ws-from-name {:data (ex-data e) :type type})))))

(defn ws-handler-http-kit [req]
  (let [ws (get-in req [:components :ws])
        ws-name (get-in req [:session :identity :username] "unknown")
        ws-channels (:ws-channels ws)
        components (:components req)]
    (kit/with-channel req ws-ch
      (connect! ws-ch ws-channels ws-name)
      (kit/on-close ws-ch (fn [status] (disconnect! ws-ch ws-channels status)))
      (kit/on-receive ws-ch (fn [payload] (ws-router ws-ch payload components))))))

(defn send->client
  "ws-to-name for debugging purposes"
  [& {:keys [ws-to payload ws-to-name] :or {ws-to-name "unknown"}}]
  {:pre [(and ws-to (not (s/check (ws-from-server payload) payload)))]}
  (let [parsed-payload (write-str payload :json)]
    (timbre/debug (format "Sending %s to [%s]" (str payload) ws-to-name))
    (kit/send! ws-to parsed-payload)))

(defn notify-clients [payload ws-channels & [ws-from]]
  (let [parsed-payload (write-str payload :json)
        ws-from-name (get @ws-channels ws-from "server")]
    (doseq [[ws-to ws-to-name] @ws-channels
            :when (not= ws-from ws-to)]
      (send->client
       :ws-to ws-to
       :payload (assoc payload :by ws-from-name)
       :ws-to-name ws-to-name))))

(defn annotation-route
  "stores annotation to database and notifies origin"
  [data components ws-from]
  (let [{:keys [token-id ann]} data
        {db :db {ws-channels :ws-channels} :ws} components
        username (:username ann)
        ws-from-name (get @ws-channels ws-from)]
    (if (not= ws-from-name username)
      (throw (ex-info "Couldn't verify identity"
                      {:token-id token-id :reason :false-id :username username}))
      (try
        (new-token-annotation db (Integer/parseInt token-id) ann)
        (send->client
         :ws-to ws-from
         :payload {:status :ok
                   :type :annotation
                   :data {:token-id token-id}}
         :ws-to-name ws-from-name)
        (catch Exception e
          (throw (ex-info "Error while storing annotation"
                          {:token-id token-id :reason :internal-error :e (str e)})))))))

(defn notify-route
  [payload {{{ws-channels :ws-channels} :ws} :components} ws-from]
  (notify-clients payload ws-channels ws-from))
