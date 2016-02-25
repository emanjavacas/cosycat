(ns cleebo.routes.ws
  (:require [taoensso.timbre :as timbre]
            [cleebo.utils :refer [write-str read-str]]
            [cleebo.db.annotations :refer [new-token-annotation new-span-annotation]]
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

(defn connect!
  [ws-ch ws-channels ws-name]
  (timbre/info ws-name "opened channel connection")
  (swap! ws-channels assoc ws-ch ws-name))

(defn disconnect!
  [ws-ch ws-channels status]
  (let [ws-user (get @ws-channels ws-ch "unknown")]
    (timbre/info ws-user "closed the channel with status:" status)
    (swap! ws-channels dissoc ws-ch)))

(declare annotation-route notify-route send->client)
(defn on-ws-success [ws-from ws-channels payload components]
  (let [{:keys [type data]} payload]
    (case type
      :annotation (annotation-route data components ws-from)
      :notify (notify-route data components ws-from))))

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
        (send->client
         :ws-to ws-from
         :payload {:type type :status :error :data (ex-data e)}
         :ws-to-name ws-from-name)))))

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
  {:pre [(and ws-to payload)]}
  (let [parsed-payload (write-str payload :json)]
    (timbre/debug (format "Sending %s to [%s]" (str payload) ws-to-name))
    (kit/send! ws-to parsed-payload)))

(defn notify-clients [ws-channels payload & [ws-from]]
  (let [parsed-payload (write-str payload :json)
        ws-from-name (get @ws-channels ws-from "server")]
    (doseq [[ws-to ws-to-name] @ws-channels
            :when (not (and ws-from (= ws-from ws-to)))]
      (send->client
       :ws-to ws-to
       :payload (assoc payload :by ws-from-name)
       :ws-to-name ws-to-name))))

(defn annotation-route
  "stores annotation to database and notify origin"
  [data components ws-from]
  (let [{:keys [cpos ann]} data
        {db :db
         {ws-channels :ws-channels} :ws} components]
    (try
      (timbre/debug ann)
      (new-token-annotation db (Integer/parseInt cpos) ann)
      (send->client
       :ws-to ws-from
       :payload {:status :ok
                 :type :annotation
                 :data {:cpos cpos}}
       :ws-to-name (get @ws-channels ws-from))
      (catch Exception e
        (throw (ex-info "Error while storing annotation" {:cpos cpos :e (str e)}))))))

(defn notify-route
  [ws-from payload {{{ws-channels :ws-channels} :ws} :components}]
  (notify-clients ws-channels payload ws-from))
