(ns cleebo.handler
  (:require [compojure.core :refer [GET POST routes defroutes wrap-routes]]
            [compojure.route :refer [not-found resources]]
            [ring.util.response :refer [file-response]]
            [ring.middleware.anti-forgery :refer [wrap-anti-forgery]]
            [ring.middleware.reload :refer [wrap-reload]]))

(defn init []
  (println "Initiating application"))

(defn destroy []
  (println "Shutting down!"))

(defroutes handler
  (GET "/" [] (file-response "index.html" {:root "resources/public"}))
  (resources "/")
  (not-found "<p>Not found</p>"))

(defn wrap-csrf [handler]
  (wrap-anti-forgery
   handler
   {:error-response
    {:status 403
     :header {"Content-Type" "text/html; charset=utf-8"}
     :body "<p>Invalid anti-forgery token</p>"}}))

(defn wrap-internal-error [handler]
  (fn [req]
    (try
      (handler req)
      (catch Throwable t
        {:status 500
         :header {"Content-Type" "text/html; charset=utf-8"}
         :body "<p>Something bad happened. Walking three times 
                around the table should solve it.</p>"}))))

(defn wrap-base [handler]
  (-> handler
      wrap-internal-error
      wrap-csrf
      wrap-reload))

(def app
  (wrap-routes #'handler wrap-base))
