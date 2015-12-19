(ns cleebo.ws
  (:require [taoensso.timbre :as timbre]
            [org.httpkit.server :as kit]
            [clojure.core.async
             :refer [<! >! put! close! go go-loop timeout chan mult tap]]))

(defonce channels (atom #{}))

(defn connect! [ws-ch]
  (timbre/info "channel open")
  (swap! channels conj ws-ch))

(defn disconnect! [ws-ch status]
  (timbre/info "channel closed: " status)
  (swap! channels #(remove #{ws-ch} %)))

(defn notify-clients [msg]
  (doseq [channel @channels]
    (timbre/debug (str "Sending " msg " to channel: " channel))
    (kit/send! channel msg)))

(defn ws-handler-http-kit [req]
  (kit/with-channel req ws-ch
    (connect! ws-ch)
    (kit/on-close ws-ch (partial disconnect! ws-ch))
    (kit/on-receive ws-ch #(notify-clients %))))
