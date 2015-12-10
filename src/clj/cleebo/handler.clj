(ns cleebo.handler
  (:require [compojure.core :refer [GET POST routes defroutes wrap-routes]]
            [compojure.route :refer [not-found resources]]
            [cleebo.layout :refer [error-page home-page]]
            [ring.util.response :refer [response content-type redirect]]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [ring-ttl-session.core :refer [ttl-memory-store]]
            [taoensso.sente.server-adapters.http-kit :refer [sente-web-server-adapter]]
            [taoensso.sente :as sente]
            [cemerick.friend :as friend]
            [cemerick.friend.credentials :as credentials]
            [cemerick.friend.workflows :as workflows]
            [cleebo.auth :refer [users]]))

(defn init []
  (println "Initiating application"))

(defn destroy []
  (println "Shutting down!"))

;;; sente
(let [{:keys [ch-recv send-fn ajax-post-fn ajax-get-or-ws-handshake-fn connected-uids]}
      (sente/make-channel-socket! sente-web-server-adapter {:packer :edn})]
  (def ring-ajax-post                 ajax-post-fn)
  (def ring-ajax-get-or-ws-handshakes ajax-get-or-ws-handshake-fn)
  (def ch-chsk                        ch-recv)
  (def chsk-send!                     send-fn)
  (def connected-uids                 connected-uids))

(defmulti event-handler :id)
(defmethod event-handler :default
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (let [session (:session ring-req)
        uid     (:uid     session)]
    (when ?reply-fn
      (?reply-fn {:unmatched-event-as-echoed-from-server event}))))

(defn login [{session :session {user-id :user-id} :params}]
;  (assoc (redirect "/") :session (assoc session :uid user-id))
  {:status 200 :session (assoc session :uid user-id)})

(defroutes handler
  (GET "/" [] (home-page))
  (GET "/chsk" req (ring-ajax-get-or-ws-hs req))
  (POST "/chsk" req (ring-ajax-post))
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

(defn wrap-base [handler]
  (-> handler
      (wrap-defaults 
       (-> site-defaults
           (assoc-in [:security :anti-forgery]
                     {:read-token (fn [req] (-> req :params :csrf-token))
                      :error-response (error-page {:status 403 :message "Missing anti-forgery-token"})})
           (assoc-in [:session :store] (ttl-memory-store (* 60 180)))))
      wrap-internal-error
      ;; (friend/authenticate
      ;;  {:credential-fn (partial credentials/bcrypt-credential-fn users)
      ;;   :workflows [(workflows/interactive-form)]})
;      wrap-reload
      ))

(def app
  (wrap-routes #'handler wrap-base))
