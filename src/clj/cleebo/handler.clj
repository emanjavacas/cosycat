(ns cleebo.handler
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as timbre]
            [compojure.core
             :refer [GET POST ANY defroutes wrap-routes]]
            [compojure.route :refer [not-found resources]]
            [prone.middleware :refer [wrap-exceptions]]
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
            [buddy.auth :refer [authenticated?]]
            [buddy.auth.middleware
             :refer [wrap-authentication wrap-authorization]]
            [cleebo.routes.auth
             :refer [safe auth-backend login-authenticate on-login-failure signup]]
            [cleebo.routes.ws :refer [ws-handler-http-kit]]
            [cleebo.cqp :refer [cqi-query cqi-query-range]]
            [cleebo.blacklab.core :refer [bl-query bl-query-range bl-query-size]]))

(defn is-logged? [req]
  (get-in req [:session :identity]))

(defn ->int [s]
  (Integer/parseInt s))

(def about-route
  (safe
   (fn [req] (about-page :logged? (is-logged? req)))
   {:login-uri "/login" :is-ok? authenticated?}))

(def cleebo-route
  (safe
   (fn [req] (cleebo-page :csrf *anti-forgery-token*))
   {:login-uri "/login" :is-ok? authenticated?}))

(def query-route
  (safe
   (fn [{{cqi-client :cqi-client} :components
         {corpus :corpus
          query-str :query-str         
          context :context
          size :size
          from :from} :params}]
     (let [result (cqi-query
                   {:cqi-client cqi-client
                    :corpus corpus
                    :query-str query-str
                    :opts {:context (->int context)
                           :size (->int size)
                           :from (->int from)}})]
       {:status 200 :body result}))
   {:login-uri "/login" :is-ok? authenticated?}))

(def range-route
  (safe
   (fn [{{cqi-client :cqi-client} :components
         {corpus :corpus
          from :from
          to :to
          context :context} :params}]
     (let [result (cqi-query-range
                   {:cqi-client cqi-client
                    :corpus corpus
                    :from (->int from)
                    :to  (->int to)
                    :opts {:context (->int context)}})]
       {:status 200 :body result}))
   {:login-uri "/login" :is-ok? authenticated?}))

(def bl-query-route
  (safe
   (fn [{{{username :username} :identity} :session 
         {blacklab :blacklab} :components
         {corpus :corpus
          query-str :query-str         
          context :context
          size :size
          from :from} :params}]
     (let [result (bl-query blacklab
                            corpus
                            query-str
                            (->int from)
                            (+ (->int from) (->int size))
                            (->int context)
                            username)]
       {:status 200 :body result}))
   {:login-uri "/login" :is-ok? authenticated?}))

(def bl-query-range-route
  (safe
   (fn [{{{username :username} :identity} :session 
         {blacklab :blacklab} :components
         {corpus :corpus
          query-str :query-str         
          context :context
          to :to
          from :from} :params}]
     (let [result (bl-query-range
                   blacklab
                   corpus
                   (->int from)
                   (+ (->int from) (->int to))
                   (->int context)
                   username)]
       {:status 200 :body result}))
   {:login-uri "/login" :is-ok? authenticated?}))

(defroutes app-routes
  (GET "/" req (landing-page :logged? (is-logged? req)))
  (GET "/login" req (login-page :csrf *anti-forgery-token*))
  (POST "/login" req (login-authenticate on-login-failure))
  (POST "/signup" req (signup req))
  (GET "/about" req (about-route req))
  (GET "/cleebo" req (cleebo-route req))
  (ANY "/logout" req (-> (redirect "/") (assoc :session {})))
  (GET "/query" req (bl-query-route req))
  (GET "/range" req (bl-query-range-route req))  
  (GET "/ws" req (ws-handler-http-kit req))
  (resources "/")
  (not-found (error-page :status 404 :title "Page not found!!")))

;;; middleware
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
      (wrap-transit-params {:encoding :json-verbose})
      wrap-keyword-params
      wrap-params
      (wrap-transit-response {:encoding :json-verbose})
      wrap-exceptions
      wrap-internal-error))

(def web-app (wrap-routes #'app-routes wrap-base))

(defn wrap-app-component [handler components]
  (fn [req]
    (handler (assoc req :components components))))

(defn make-handler [component]
  (let [components (select-keys component (:components component))]
    (-> app-routes
        (wrap-app-component components)
        (wrap-routes wrap-base))))

