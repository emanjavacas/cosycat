(ns cleebo.routes.auth
  (:require [taoensso.timbre :as timbre]
            [clj-time.core :as time]
            [compojure.response :refer [render]]
            [ring.util.response :refer [redirect response]]
            [ring.middleware.anti-forgery :refer [*anti-forgery-token*]]
            [cleebo.components.ws :refer [send-clients]]
            [cleebo.components.blacklab :refer [remove-hits!]]
            [cleebo.db.users :refer [lookup-user is-user? new-user normalize-user]]
            [cleebo.db.projects :refer [new-project]]
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
  [{{username :username firstname :firstname lastname :lastname email :email
     password :password repeatpassword :repeatpassword :as user} :params
    {db :db ws :ws} :components
    {next-url :next} :session :as req}]
  (let [user (dissoc user :repeatpassword)]
    (cond
      (not (= password repeatpassword)) (on-signup-failure req "Password mismatch")
      (is-user? db user)                (on-signup-failure req "User already exists")
      :else (let [user (-> (new-user db user) (assoc :active true))]
              (new-project db username) ;create default project
              (send-clients ws {:type :signup :data (normalize-user user :projects)})
              (-> (redirect (or next-url "/")) (assoc-in [:session :identity] user))))))

(defn login-route
 [{{username :username password :password} :params
   {username-form :username password-form :password} :form-params
   {db :db ws :ws} :components
   {next-url :next} :session :as req}]
  (let [username (or username username-form)
        password (or password password-form)
        user {:username username :password password}]
    (if-let [user (lookup-user db user)]
      (let [user (assoc user :active true)]
        (send-clients ws {:type :login :data (normalize-user user :project)})
        (-> (redirect (or next-url "/"))
            (assoc-in [:session :identity] user)))
      (on-login-failure req))))

(defn logout-route
  [{{{username :username} :identity} :session
    {blacklab :blacklab ws :ws} :components}]
  (remove-hits! blacklab username)
  (send-clients ws {:type :logout :data {:username username}})
  (-> (redirect "/") (assoc :session {})))

(defn jws-login-route
  [{{username :username password :password} :params
    {username-form :username password-form :password} :form-params
    {db :db} :components
    {next-url :next} :session :as req}]
  (let [username (or username username-form)
        password (or password password-form)
        user {:username username :password password}]
    (if-let [user (lookup-user db user)]
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

(defn is-logged? [req]
  (get-in req [:session :identity]))
