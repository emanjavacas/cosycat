(ns cleebo.handler
  (:require [compojure.core :refer [GET POST ANY routes defroutes wrap-routes]]
            [compojure.route :refer [not-found resources]]
            [taoensso.timbre :as timbre]
            [prone.middleware :refer [wrap-exceptions]]
            [cleebo.layout :refer [error-page home-page landing-page]]
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
            [cemerick.friend.workflows :as wfs]))

(def debug-token (atom nil))
(def app-users
  {"root" {:username "admin"
           :password (creds/hash-bcrypt "pass")
           :roles #{::admin}}
   "user" {:username "user"
           :password (creds/hash-bcrypt "pass")
           :roles #{::user}}})

(defn login-workflow [{:keys [params] :as req}]
  (let [creds {:username (get params :username)
               :password (get params :password)}
        cred-fn (get-in req [::friend/auth-config :credential-fn])]
    (wfs/make-auth (cred-fn creds))))

(defn login-cred-fn [{:keys [username password] :as login-data}]
  (if-let [creds (creds/bcrypt-credential-fn app-users login-data)]
    creds))

(defn fun-wf [req]
  (timbre/debug "workflow called with " (str req))
  (let [speak (get-in req [:params :username])]
    (when (= speak "friend")
      (wfs/make-auth {:identity "user" :roles #{::user}}))))

(defn get-token [req]
  (timbre/debug "anti" req)
  (get-in req [:params :csrf]))

(defroutes handler
  (GET "/" [] (landing-page {:csrf *anti-forgery-token*}))
  (GET "/auth" req (friend/authorize #{::user} "Only for users"))
  (POST "/logout" req (redirect "/"))
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

(defn wrap-debug [handler]
  (fn [req]
    (timbre/info "debug")
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
        :default-landing-uri "/auth"})                                                      
      (wrap-anti-forgery {:read-token get-token})
      (wrap-session {:store (ttl-memory-store (* 30 60))})
      wrap-transit-params
      wrap-keyword-params
      wrap-params
      (wrap-transit-response {:encoding :json-verbose})
      wrap-exceptions
      wrap-internal-error))

(def app (wrap-routes #'handler wrap-base))
