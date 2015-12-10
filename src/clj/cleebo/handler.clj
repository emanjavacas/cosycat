(ns cleebo.handler
  (:require [compojure.core :refer [GET POST routes defroutes wrap-routes]]
            [compojure.route :refer [not-found resources]]
            [ring.util.response :refer [file-response response]]

            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            
            [ring.middleware.anti-forgery :refer [wrap-anti-forgery]]
            [ring.middleware.reload :refer [wrap-reload]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.nested-params :refer [wrap-nested-params]]
            [ring.middleware.session :refer [wrap-session]]              

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
      (sente/make-channel-socket! sente-web-server-adapter)]
  (def ring-ajax-post         ajax-post-fn)
  (def ring-ajax-get-or-ws-hs ajax-get-or-ws-handshake-fn)
  (def ch-chsk                ch-recv)
  (def chsk-send!             send-fn)
  (def connected-uids         connected-uids))

(defroutes handler
  (GET "/" [] (file-response "index.html" {:root "resources/public"}))
  (GET "/login" [] (file-response "index.html" {:root "resources/public"}))
  (GET "/debug" [req] (fn [req] {:status 200 :header {"Content-Type" "text/html" :body req}}))
;  (resources "/")
  (GET "/chsk" req (ring-ajax-get-or-ws-hs req))
  (POST "/chsk" req (ring-ajax-post))
  (not-found "<p>Not found.</p>"))

;; (defn wrap-csrf [handler]
;;   (wrap-anti-forgery
;;    handler
;;    {:error-response
;;     {:status 403
;;      :header {"Content-Type" "text/html; charset=utf-8"}
;;      :body "<p>Invalid anti-forgery token</p>"}}))

;; (defn wrap-internal-error [handler]
;;   (fn [req]
;;     (try
;;       (handler req)
;;       (catch Throwable t
;;         {:status 500
;;          :header {"Content-Type" "text/html; charset=utf-8"}
;;          :body "<p>Something bad happened. Walking three times 
;;                 around the table may solve it.</p>"}))))

(defn wrap-base [handler]
  (-> (wrap-defaults handler site-defaults)
      ;; (friend/authenticate
      ;;  {:credential-fn (partial credentials/bcrypt-credential-fn users)
      ;;   :workflows [(workflows/interactive-form)]})
;      wrap-reload
      ))

(def app
  (wrap-routes #'handler wrap-base))


