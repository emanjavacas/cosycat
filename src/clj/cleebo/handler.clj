(ns cleebo.handler
  (:require [compojure.core :refer [GET POST routes defroutes wrap-routes]]
            [compojure.route :refer [not-found resources]]
            [cleebo.layout :refer [error-page home-page]]
            [ring.util.response :refer [response content-type redirect]]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [ring-ttl-session.core :refer [ttl-memory-store]]
            [taoensso.timbre :as timbre]            
            [taoensso.sente.server-adapters.http-kit
             :refer [sente-web-server-adapter]]
            [taoensso.sente :as sente]
            [cemerick.friend :as friend]
            [cemerick.friend.credentials :as credentials]
            [cemerick.friend.workflows :as workflows]
            [cleebo.auth :refer [users]]))

(defn init []
  (timbre/info "Initiating application"))

(defn destroy []
  (timbre/info "Shutting down!"))

;;; sente
(let [{:keys
       [ch-recv send-fn ajax-post-fn ajax-get-or-ws-handshake-fn connected-uids]}
      (sente/make-channel-socket! sente-web-server-adapter {:packer :edn})]
  (def ring-ajax-post                 ajax-post-fn)
  (def ring-ajax-get-or-ws-handshakes ajax-get-or-ws-handshake-fn)
  (def ch-chsk                        ch-recv)
  (def chsk-send!                     send-fn)
  (def connected-uids                 connected-uids))

(defmulti event-msg-handler :id)
(defn event-msg-handler* [{:as ev-msg :keys [id ?data event]}]
  (event-msg-handler ev-msg))
(defmethod event-msg-handler :default
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (let [session (:session ring-req)
        uid     (:uid     session)]
    (timbre/debugf (str "Unhandled event: " event))
    (when ?reply-fn
      (?reply-fn {:unmatched-event-as-echoed-from-server event}))))

(defn login [params]
  (let [{session :session {user-id :user-id} :params} params]
    (timbre/infof (str "Login request: " params))
    {:status 200 :session (assoc session :uid user-id)}))

(defroutes handler
  (GET "/" [] (home-page))
  (GET "/ws" req (ring-ajax-get-or-ws-handshakes req))
  (POST "/ws" req (ring-ajax-post req))
  (POST "/login" req (login req))
  (GET "/debug" req (error-page {:message (str req)}))
  (resources "/")
  (not-found (error-page {:status 404 :title "Page not found"})))

(defn wrap-internal-error [handler]
  (fn [req]
    (try
      (handler req)
      (catch Throwable t
        (error-page
         {:status 500
          :title "Something very bad happened!"
          :message (str t)})))))

;; (defn wrap-base [handler]
;;   (-> handler
;;       (wrap-defaults 
;;        (-> site-defaults
;;            (assoc-in [:security :anti-forgery]
;;                      {:read-token (fn [req] (-> req :params :csrf-token))
;;                       :error-response (error-page {:status 403 :message "Missing anti-forgery-token"})})
;;            (assoc-in [:session :store] (ttl-memory-store (* 60 180)))))
;;       wrap-internal-error
;;       ;; (friend/authenticate
;;       ;;  {:credential-fn (partial credentials/bcrypt-credential-fn users)
;;       ;;   :workflows [(workflows/interactive-form)]})
;; ;      wrap-reload
;;       ))

;; (def app
;;   (wrap-routes #'handler wrap-base))

(def app
  (let [ring-config
        (assoc-in site-defaults [:security :anti-forgery]
                  {:read-token (fn [req] (-> req :params :csrf-token))})]
    (wrap-defaults handler ring-config)))

(defonce router (atom nil))
(defn stop-router! [] (when-let [stop-fn @router] (stop-fn)))
(defn start-router! []
  (stop-router!)
  (reset! router (sente/start-chsk-router! ch-chsk event-msg-handler*)))



;; (prn @connected-uids)
;; (chsk-send! "asd" [:some/serverpush {:hello "hello"}])



