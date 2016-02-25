(ns cleebo.ws
  (:require [cognitect.transit :as t]
            [re-frame.core :as re-frame]
            [taoensso.timbre :as timbre]))

(defonce ws-ch (atom nil))
(def json-reader (t/reader :json-verbose))
(def json-writer (t/writer :json-verbose))

(defn receive-transit-msg! [wrap-msg]
  (fn [msg]
    (if @ws-ch
      (wrap-msg
       (->> msg .-data (t/read json-reader))))))

(defn send-transit-msg! [msg]
  (if @ws-ch
    (let [json-msg (t/write json-writer msg)]
      (.send @ws-ch json-msg))
    (throw (js/Error. "Websocket is not available!"))))

(defn make-ws-ch [url wrap-msg]
  (timbre/info "Attempting connection to " url)
  (if-let [c (js/WebSocket. url)]
    (do
      (set! (.-onmessage c) (receive-transit-msg! wrap-msg))
      (reset! ws-ch c)
      (timbre/info "Connected to " url))
    (throw (js/Error. "Websocket connection failed!"))))

(defn set-ws-ch []
  (make-ws-ch
   (str "ws://" (.-host js/location) "/ws")
   #(re-frame/dispatch [:receive-ws %])))
