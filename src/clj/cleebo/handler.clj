(ns cleebo.handler
  (:require [compojure.core :refer [GET POST ANY routes defroutes wrap-routes]]
            [compojure.route :refer [not-found resources]]
            [taoensso.timbre :as timbre]
            [prone.middleware :refer [wrap-exceptions]]
            [cleebo.views.layout
             :refer [error-page cleebo-page landing-page about-page login-page]]
            [ring.util.response :refer [response redirect]]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [ring.middleware.anti-forgery
             :refer [wrap-anti-forgery *anti-forgery-token*]]
            [ring.middleware.transit
             :refer [wrap-transit-response wrap-transit-params]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.nested-params :refer [wrap-nested-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.session :refer [wrap-session]]
            [ring.middleware.reload :refer [wrap-reload]]
            [ring-ttl-session.core :refer [ttl-memory-store]]
            [cemerick.friend :as friend]
            [cemerick.friend.credentials :as creds]
            [cemerick.friend.workflows :as wfs]
            [chord.http-kit :refer [with-channel]]
            [org.httpkit.server :as kit]
            [clojure.core.async
             :refer [<! >! put! close! go go-loop timeout chan mult tap]]))

(def debug-token (atom nil))
(defonce channels (atom #{}))
(def app-users
  {"root" {:username "root"
           :password (creds/hash-bcrypt "pass")
           :roles #{::admin}}
   "user" {:username "user"
           :password (creds/hash-bcrypt "pass")
           :roles #{::user}}})

(derive ::admin ::user)

(defn ws-handler [req]
  (with-channel req ws-ch {:format :transit-json}
    (let [secs (atom 0)]
      (go
        (let [{:keys [message] :as load} (<! ws-ch)]
          (timbre/debug "Message received: " load)
          (<! (timeout 10000))
          (timbre/debug "Waited " (str (swap! secs + 10000)))
          (>! ws-ch (str "Hello from server!" (rand-int 10)))
          (timbre/debug "Sent message"))))))

(declare connect! disconnect! notify-clients)
(defn ws-handler-http-kit [req]
  (kit/with-channel req ws-ch
    (connect! ws-ch)
    (kit/on-close ws-ch (partial disconnect! ws-ch))
    (kit/on-receive ws-ch #(notify-clients %))))

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

(defn is-logged? [req]
  (get-in req [:session ::friend/identity]))

(defroutes handler
  (GET "/" req (landing-page :logged? (is-logged? req)))
  (GET "/login" req (login-page :csrf *anti-forgery-token*))
  (GET "/debug" req (error-page  :message (str req)))
  (GET "/about" req (friend/authorize #{::admin} (about-page :logged? (is-logged? req))))
  (GET "/cleebo" req (friend/authorize #{::user} (cleebo-page :csrf *anti-forgery-token*)))
  (ANY "/logout" req (friend/logout* (redirect "/")))
  (GET "/ws" req (ws-handler-http-kit req))
  (resources "/")
  (not-found (error-page :status 404 :title "Page not found")))

(defn get-token [req]
  (get-in req [:params :csrf]))

(defn wrap-internal-error [handler]
  (fn [req]
    (try
      (handler req)
      (catch Throwable t
        (error-page
         {:status 500
          :title "Something very bad happened!"
          :message (str t)})))))

(defn wrap-debug [handler]
  (fn [req]
    (timbre/info req)
    (handler req)))

(defn wrap-base [handler]
  (-> handler   
      wrap-debug
      wrap-reload
      (friend/authenticate
       {:credential-fn (partial creds/bcrypt-credential-fn app-users)
        :workflows [(wfs/interactive-form)]
        :login-uri "/login"})  
      (wrap-anti-forgery {:read-token get-token})
      wrap-session
      ;(wrap-session {:store (ttl-memory-store (* 30 60))})
      wrap-transit-params
      wrap-keyword-params
      wrap-params
      (wrap-transit-response {:encoding :json-verbose})
      wrap-exceptions
      wrap-internal-error))

(def app (wrap-routes #'handler wrap-base))
