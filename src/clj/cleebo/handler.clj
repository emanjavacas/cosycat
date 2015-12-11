(ns cleebo.handler
  (:require [compojure.core :refer [GET POST routes defroutes wrap-routes]]
            [compojure.route :refer [not-found resources]]
            [cleebo.layout :refer [error-page home-page]]
            [ring.util.response :refer [response resource-response]]
            [ring.middleware.json
             :refer [wrap-json-response wrap-json-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [ring.middleware.session :refer [wrap-session]]
            [ring-ttl-session.core :refer [ttl-memory-store]]
            [taoensso.timbre :as timbre]            
            [cemerick.friend :as friend]
            [cemerick.friend.credentials :as credentials]
            [cemerick.friend.workflows :as workflows]
            [cleebo.auth :refer [users]]))

(defn init []
  (timbre/info "Initiating application"))

(defn destroy []
  (timbre/info "Shutting down!"))

;; (defn json-response [data & [status]]
;;   {:status (or status 200)
;;    :headers {"Content-Type" "application/json"}
;;    :body data})

(defn login-handler [req]
  (let [{session :session {user-id :user-id message :message} :params} req]
    (timbre/debug req)
    {:message user-id :status 200 :session (assoc session :user-id user-id)}))

(defroutes handler
  (GET "/" [] (home-page))
  (POST "/login" req (login-handler req))  
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
           (assoc-in [:security :anti-forgery] false)
           (assoc-in [:session :store] (ttl-memory-store (+ 60 40)))))
      wrap-keyword-params
      wrap-json-params
      wrap-json-response
      wrap-internal-error
      ;(wrap-session {:store (ttl-memory-store (* 60 30))}) 
      ;; (friend/authenticate
      ;;  {:credential-fn (partial credentials/bcrypt-credential-fn users)
      ;;   :workflows [(workflows/interactive-form)]})
      ))

(def app (wrap-routes #'handler wrap-base))
