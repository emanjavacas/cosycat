(ns cleebo.auth
  (:require [cemerick.friend.workflows :as workflows]
            [cemerick.friend.credentials :as credentials]))

; a dummy in-memory user "database"
(def users
  {"root" {:username "root"
           :password (credentials/hash-bcrypt "admin_password")
           :roles #{::admin}}
   "user" {:username "user"
           :password (credentials/hash-bcrypt "mbg_password")
           :roles #{::user}}})



