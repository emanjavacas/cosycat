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
            [chord.http-kit :refer [with-channel wrap-websocket-handler]]
            [clojure.core.async :refer [<! >! put! close! go]]))

(def debug-token (atom nil))
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
    (go
      (let [{:keys [message]} (<! ws-ch)]
        (timbre/debug "Message received: " message)
        (>! ws-ch "Hello from server!")
        (close! ws-ch)))))

(defn is-logged? [req]
  (get-in req [:session ::friend/identity]))

(defroutes handler
  (GET "/" req (landing-page :logged? (is-logged? req)))
  (GET "/login" req (login-page :csrf *anti-forgery-token*))
  (GET "/debug" req (error-page  :message (str req)))
  (GET "/about" req (friend/authorize #{::admin} (about-page :logged? (is-logged? req))))
  (GET "/cleebo" req (friend/authorize #{::user} (cleebo-page :csrf *anti-forgery-token*)))
  (ANY "/logout" req (friend/logout* (redirect "/")))
  (GET "/ws" req (ws-handler req))
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
    (reset! debug-token req)
    (handler req)))

(defn wrap-base [handler]
  (-> handler   
      wrap-debug
      wrap-reload
      (friend/authenticate
       {:credential-fn (partial creds/bcrypt-credential-fn app-users)
        :workflows [(wfs/interactive-form)]
        :login-uri "/login"
        ;:default-landing-uri "/cleebo"
        })  
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
;;; static routes
;;; spa routes (ws) ;; secure
