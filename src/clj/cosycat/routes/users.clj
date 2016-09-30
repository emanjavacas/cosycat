(ns cosycat.routes.users
  (:require [compojure.core :refer [routes context POST GET]]
            [cosycat.routes.utils :refer [make-default-route]]
            [cosycat.db.users :as users]
            [cosycat.db.utils :refer [normalize-user]]
            [cosycat.avatar :refer [user-avatar is-gravatar?]]
            [cosycat.components.ws :refer [send-clients]]
            [config.core :refer [env]]
            [taoensso.timbre :as timbre]))

(defn maybe-update-avatar
  [{new-email :email username :username {href :href} :avatar :as update-map} old-email]
  (if (and new-email (not= new-email old-email) (is-gravatar? href))
    (if-let [avatar (user-avatar username new-email)]
      (assoc update-map :avatar avatar)
      update-map)
    update-map))

(defn update-profile-route
  [{{{username :username} :identity} :session {db :db ws :ws} :components
    {update-map :update-map} :params}]
  (let [{old-email :email} (users/user-info db username)
        new-user-info (-> (users/update-user-info db username update-map)
                          (maybe-update-avatar old-email)
                          normalize-user)]
    (send-clients
     ws {:type :new-user-info :data {:update-map new-user-info :username username}}
     :source-client username)
    new-user-info))

(defn new-avatar-route
  [{{{username :username} :identity} :session {db :db ws :ws} :components}]
  (let [{:keys [email]} (users/user-info db username)
        avatar (user-avatar username email)]
    (users/update-user-info db username {:avatar avatar})
    (send-clients
     ws {:type :new-user-avatar :data {:avatar avatar :username username}} :source-client username)
    avatar))

(defn users-routes []
  (routes
   (context "/users" []
     (POST "/update-profile" [] (make-default-route update-profile-route))
     (POST "/new-avatar" [] (make-default-route new-avatar-route)))))
