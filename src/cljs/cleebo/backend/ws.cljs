(ns cleebo.backend.ws
  (:require [cognitect.transit :as t]
            [cleebo.utils :refer [time-id]]
            [re-frame.core :as re-frame]
            [cljs.core.async :refer [chan <! >! put! take! close!]]
            [taoensso.timbre :as timbre])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defonce socket-atom (atom nil))
(def json-reader (t/reader :json-verbose))
(def json-writer (t/writer :json-verbose))

(defn open-ws-channel
  [{:keys [url retry-count retried-count] :or {retry-count 10} :as opts}]
  (if-let [socket (js/WebSocket. url)]
    (let [retried-count (or retried-count 0)]
      (set! (.-onopen socket) (fn [x]
                                (reset! socket-atom socket)
                                (timbre/info "Opened WS connection")))
      (set! (.-onclose socket) (fn [x]
                                 (reset! socket-atom nil)
                                 (when (< retried-count retry-count)
                                   (js/setTimeout
                                    (fn []
                                      (open-ws-channel
                                       (assoc opts :retried-count (inc retried-count))))
                                    (min 10000 (+ 2000 (* 500 retried-count)))))
                                 (timbre/info "WS closed!")))
      (set! (.-onerror socket) (fn [x] (timbre/info "WS error")))
      (set! (.-onmessage socket)
            ;; record message history?
            (fn [payload]
              (let [parsed-payload (->> payload .-data (t/read json-reader))]
                (re-frame/dispatch [:ws parsed-payload])))))
    (throw (js/Error "Websocket is not available!"))))

(defn send-ws [payload]
  (if-let [socket @socket-atom]
    (->> (assoc payload :payload-id (time-id))
         (t/write json-writer)
         (.send socket))
    (js/Error. "Websocket is not available!")))
