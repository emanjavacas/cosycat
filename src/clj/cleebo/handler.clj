(ns cleebo.handler
  (:require [com.stuartsierra.component :as component]
            [compojure.core
             :refer [GET POST ANY routes defroutes wrap-routes]]
            [compojure.route :refer [not-found resources]]
            [compojure.response :refer [render]]            
            [taoensso.timbre :as timbre]
            [prone.middleware :refer [wrap-exceptions]]
            [cleebo.db :refer [lookup-user is-user? new-user]]
            [cleebo.views.error :refer [error-page]]
            [cleebo.views.cleebo :refer [cleebo-page]]
            [cleebo.views.landing :refer [landing-page]]
            [cleebo.views.about :refer [about-page]]
            [cleebo.views.login :refer [login-page]]
            [ring.util.response :refer [redirect response]]
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
            [buddy.auth :refer [authenticated? throw-unauthorized]]
            [buddy.auth.backends.session :refer [session-backend]]
            [buddy.auth.middleware
             :refer [wrap-authentication wrap-authorization]]
            [buddy.auth.accessrules :refer [restrict]]
            [chord.http-kit :refer [with-channel]]
            [org.httpkit.server :as kit]
            [clojure.core.async
             :refer [<! >! put! close! go go-loop timeout chan mult tap]]))

(defonce channels (atom #{}))
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

(defn on-login-failure [req]
  (render
   (login-page
    :csrf *anti-forgery-token*
    :error-msg "Invalid credentials")
   req))

(defn on-signup-failure [req msg]
  (render
   (login-page
    :csrf *anti-forgery-token*
    :error-msg msg)
   req))

(defn signup [req]
  (let [{{:keys [password repeatpassword username]} :params session :session} req
        db (get-in req [::components :db])
        user {:username username :password password}
        is-user (is-user? db user)
        password-match (= password repeatpassword)]
    (timbre/debug user is-user db password-match)
    (cond
      (not password-match) (on-signup-failure req "Password mismatch")
      is-user              (on-signup-failure req "User already exists")
      :else (let [user (new-user db user)
                  new-session (assoc session :identity user)]
              (-> (redirect (get-in req [:session :next] "/"))
                  (assoc :session new-session))))))

(defn get-username [params form-params]
  (or (get form-params "username") (:username params "")))

(defn get-password [params form-params]
  (or (get form-params "password") (:password params "")))

(defn login-authenticate [on-login-failure]
  (fn [req]
    (let [{:keys [params form-params session]} req
          db (get-in req [::components :db])
          username (get-username params form-params)
          password (get-password params form-params)]
      (if-let [user (lookup-user db username password)]
        (let [new-session (assoc session :identity user)]
          (-> (redirect (get-in req [:session :next] "/"))
              (assoc :session new-session)))
        (on-login-failure req)))))

(defn unauthorized-handler [req meta]
  (-> (response "Unauthorized")
      (assoc :status 403)))

(def auth-backend
  (session-backend {:unauthorized-handler unauthorized-handler}))

(defn safe [handler rule-map]
  (fn [req]
    (let [{:keys [login-uri is-ok?]} rule-map]
      (if (is-ok? req)
        (handler req)
        (-> (redirect login-uri)
            (assoc-in [:session :next] (:uri req)))))))

(defn is-logged? [req]
  (get-in req [:session :identity]))

(defroutes app-routes
  (GET "/" req (landing-page :logged? (is-logged? req)))
  (GET "/login" req (login-page :csrf *anti-forgery-token*))
  (POST "/login" req (login-authenticate on-login-failure))
  (POST "/signup" req (signup req))
  (GET "/about" req (safe
                     (fn [req] (about-page :logged? (is-logged? req)))
                     {:login-uri "/login" :is-ok? authenticated?}))
  (GET "/cleebo" req (safe
                      (fn [req] (cleebo-page :csrf *anti-forgery-token*))
                      {:login-uri "/login" :is-ok? authenticated?}))
  (ANY "/logout" req (-> (redirect "/") (assoc :session {})))
  (GET "/ws" req (ws-handler-http-kit req))
  (resources "/")
  (not-found (error-page :status 404 :title "Page not found")))

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
      (wrap-authorization auth-backend)
      (wrap-authentication auth-backend)
      (wrap-anti-forgery {:read-token (fn [req] (get-in req [:params :csrf]))})
      (wrap-session {:store (ttl-memory-store (* 30 60))})
      wrap-transit-params
      wrap-keyword-params
      wrap-params
      (wrap-transit-response {:encoding :json-verbose})
      wrap-exceptions
      wrap-internal-error))

(def web-app (wrap-routes #'app-routes wrap-base))

(defn wrap-app-component [handler components]
  (fn [req]
    (handler (assoc req ::components components))))

(defn make-handler [component]
  (let [components (select-keys component (:components component))]
    (-> app-routes
        (wrap-app-component components)
        (wrap-routes wrap-base))))
