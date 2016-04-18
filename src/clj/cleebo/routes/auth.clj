(ns cleebo.routes.auth
  (:require [taoensso.timbre :as timbre]
            [clj-time.core :as time]
            [compojure.response :refer [render]]
            [ring.util.response :refer [redirect response]]
            [ring.middleware.anti-forgery :refer [*anti-forgery-token*]]
            [cleebo.components.ws :refer [notify-clients]]
            [cleebo.db.users :refer [lookup-user is-user? new-user filter-user-public]]
            [cleebo.db.projects :refer [new-project]]
            [cleebo.views.error :refer [error-page]]
            [cleebo.views.login :refer [login-page]]
            [buddy.auth.backends.session :refer [session-backend]]
            [buddy.sign.jws :as jws]
            [buddy.auth.backends.token :refer [jws-backend]]))

(def secret "mysupercomplexsecret")

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

(defn signup-route
   [{{username :username password :password repeatpassword :repeatpassword} :params
   {db :db ws :ws} :components
   {next-url :next} :session :as req}]
  (let [user {:username username :password password}
        is-user (is-user? db user)
        password-match? (= password repeatpassword)]
    (cond
      (not password-match?) (on-signup-failure req "Password mismatch")
      is-user               (on-signup-failure req "User already exists")
      :else (let [user (new-user db user)]
              (new-project db username) ;create default project
              (notify-clients ws {:type :notify
                                  :data (filter-user-public user)
                                  :status :signup})
              (-> (redirect (or next-url "/"))
                  (assoc-in [:session :identity] user))))))

(defn login-route
 [{{username :username password :password} :params
   {username-form :username password-form :password} :form-params
   {db :db ws :ws} :components
   {next-url :next} :session :as req}]
  (let [username (or username username-form)
        password (or password password-form)]
    (if-let [user (lookup-user db username password)]
      (do (notify-clients ws {:type :notify
                              :data (filter-user-public user)
                              :status :login})
          (-> (redirect (or next-url "/"))
              (assoc-in [:session :identity] user)))
      (on-login-failure req))))

(defn jws-login-route
  [{{username :username password :password} :params
    {username-form :username password-form :password} :form-params
    {db :db} :components
    {next-url :next} :session :as req}]
  (let [username (or username username-form)
        password (or password password-form)]
    (if-let [user (lookup-user db username password)]
      (let [claims {:user user
                    :exp (-> 3 time/hours time/from-now)}
            token (jws/sign claims secret)] ;check this
        {:status 200
         :headers {:content-type "application/json"}
         :body {:token token}})
      (on-login-failure req))))

(defn unauthorized-handler [req meta]
  (-> (response "Unauthorized")
      (assoc :status 403)))

(def auth-backend
  (session-backend {:unauthorized-handler unauthorized-handler}))

(def token-backend
  (jws-backend {:secret secret}))

(defn safe [handler rule-map]
  (fn [req]
    (let [{:keys [login-uri is-ok?]} rule-map]
      (if (is-ok? req)
        (handler req)
        (-> (redirect login-uri)
            (assoc-in [:session :next] (:uri req)))))))

(defn is-logged? [req]
  (get-in req [:session :identity]))
