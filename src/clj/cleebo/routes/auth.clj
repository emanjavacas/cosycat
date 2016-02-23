(ns cleebo.routes.auth
  (:require [taoensso.timbre :as timbre]
            [compojure.response :refer [render]]
            [ring.util.response :refer [redirect response]]
            [ring.middleware.anti-forgery :refer [*anti-forgery-token*]]           
            [cleebo.db.users :refer [lookup-user is-user? new-user]]
            [cleebo.views.error :refer [error-page]]
            [cleebo.views.login :refer [login-page]]
            [buddy.auth.accessrules :refer [restrict]]
            [buddy.auth.backends.session :refer [session-backend]]))

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
        db (get-in req [:components :db])
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
          db (get-in req [:components :db])
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
