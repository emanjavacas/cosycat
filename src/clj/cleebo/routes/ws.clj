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
      (assoc component :ws-channels (atom #{}))))
  (stop [component]
    (assoc component :ws-channels nil)))

(defn new-ws []
  (map->WS {}))

(declare connect! disconnect! on-ws-success send->client)

(defn ws-handler-http-kit [req]
  (let [ws (get-in req [:components :ws])
        components (dissoc (:components req) :ws)
        user (get-in req [:session :identity :username])
        ws-channels (:ws-channels ws)
        ws-router (fn [ws-from payload]
                    (let [parsed-payload (read-str payload :json)
                          {:keys [type data]} parsed-payload]
                      (timbre/debug "Received " (str parsed-payload))
                      (try
                        (on-ws-success ws-from ws-channels user parsed-payload components)
                        (catch Exception e
                          (send->client ws-from {:data (str e) :status :error})))))]
    (kit/with-channel req ws-ch
      (connect! ws-ch ws-channels)
      (kit/on-close ws-ch (partial disconnect! ws-ch ws-channels))
      (kit/on-receive ws-ch (partial ws-router ws-ch)))))

(defn connect! [ws-ch ws-channels]
  (timbre/info "channel open")
  (swap! ws-channels conj ws-ch))

(defn disconnect! [ws-ch ws-channels status]
  (timbre/info "channel closed: " status)
  (swap! ws-channels (partial remove #{ws-ch})))

(defn notify-clients [ws-from ws-channels payload]
  (let [out-str (write-str payload :json)]
    (doseq [channel @ws-channels]
      (timbre/debug (format "Sending %s from [%s] to [%s]" (str payload) ws-from ws-from))
      (kit/send! channel out-str))))

(defn send->client [ws-from payload]
  (let [out-str (write-str payload :json)]
    (timbre/debug (format "Sending %s from [%s] to [%s]" (str payload) ws-from ws-from))
    (kit/send! ws-from out-str)))

(defn on-ws-error [ws-from ws-channels {:keys [type data] :as payload}]
  (send->client ws-from (update payload :status :error)))

(defn on-ws-success [ws-from ws-channels user payload & [components]]
  (let [{:keys [type data]} payload]
    (case type
      :annotation (let [{:keys [cpos ann]} data
                        db (:db components)]
                    (timbre/debug cpos ann)
                    (new-token-annotation db (Integer/parseInt cpos) user ann))
      :msgs (notify-clients ws-from ws-channels payload))))
