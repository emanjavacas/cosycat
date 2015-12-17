(ns cleebo.pages.query
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [re-com.core :as re-com]
            ;[chord.client :refer [ws-ch]]
            [cljs.core.async :refer [<! >! put! close! timeout chan]]
            [taoensso.timbre :as timbre]
            [cognitect.transit :as t])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def url "ws://146.175.15.30:3449/ws")
;; (defn in-chan [url & {:keys [format] :or {:format :transit-json}}]
;;   (let [out (chan)]
;;     (go
;;       (loop [in (:ws-channel (<! (ws-ch url {:format format})))]
;;         (let [{:keys [message error] :as data} (<! in)]
;;           (if data
;;             (do (cond message (>! out message)
;;                       error   (close! in))
;;                 (recur in))
;;             (do (timbre/debug "Connection failed!")
;;                 (<! (timeout 1000))
;;                 (recur (:ws-channel (<! (ws-ch url {:format format})))))))))
;;     out))

;; (defn out-chan [url & {:keys [format] :or {:format :transit-json}}]
;;   (let [in (chan)]
;;     (go
;;       (loop [out (:ws-channel (<! (ws-ch url {:format format})))]
;;         (let [data (<! in)]
;;           (>! out data)
;;           (recur out))))
;;     in))

(defonce ws-ch (atom nil))
(def json-reader (t/reader :json-verbose))
(def json-writer (t/writer :json-verbose))

(defn by-id [id]
  (.getElementById js/document id))

(defn receive-transit-msg! [wrap-msg]
  (fn [msg]
    (if @ws-ch
      (wrap-msg
       (->> msg .-data (t/read json-reader))))))

(defn send-transit-msg! [msg]
  (if @ws-ch
    (let [json-msg (t/write json-writer msg)]
      (timbre/debug "Sent" json-msg)
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

(defn query-field []
  [:h2.page-header {:style {:font-weight "5em"}}
   [:div.row
    [:div.col-sm-3 "Query Panel"]
    [:div.col-sm-9
     [:div.form-horizontal
      [:div.input-group      
       [:input.form-control
        {:name "query"
         :type "text"
         :id "query"
         :placeholder "Example: [pos='.*\\.']"
         :autocorrect "off"
         :autocapitalize "off"
         :spellcheck "false"}]
       [:span.input-group-addon
        [re-com/md-icon-button
         :md-icon-name "zmdi-search"
         :size :smaller
         :on-click #(let [text (.-value (by-id "query"))]
                      (send-transit-msg! {:text text}))]]]]]]])

(defn results-frame []
  (let [messages (re-frame/subscribe [:input-msg])]
    (fn []
      [re-com/v-box :children
       [[re-com/h-box :align :center
         :children
         [[re-com/md-icon-button
           :md-icon-name "zmdi-edit"
           :on-click #(send-transit-msg! {:text "Hello everyone!"})]
          [re-com/md-icon-button
           :md-icon-name "zmdi-copy"
           :on-click #(re-frame/dispatch [:remove-last nil])]]]
        [re-com/box
         :child
         [:div
          [:ul (for [[i msg] (map-indexed vector (reverse @messages))]
                 ^{:key i}
                 [:li (:text (second msg))])]]]]])))

(defn annotation-frame []
  [:div "annotation frame"])

(defn query-main []
  (let [annotation? (atom true)]
    (fn []
      [re-com/v-box :gap "50px"
       :children 
       [[re-com/box :align :center :child [results-frame]]
        (when @annotation?
          [re-com/box :align :center :child [annotation-frame]])]])))

(defn query-panel []
  [:div
   [query-field]
   [query-main]])
