(ns cleebo.db.projects
  (:require [monger.collection :as mc]
            [monger.operators :refer :all]
            [schema.core :as s]
            [cleebo.schemas.project-schemas :refer [project-schema update-schema]]
            [cleebo.roles :refer [check-project-role]]
            [cleebo.db.users :refer [is-user?]]
            [cleebo.components.db :refer [new-db colls]]
            [taoensso.timbre :as timbre]))

(defn is-project? [{db-conn :db} project-name]
  (boolean (mc/find-one-as-map db-conn (:projects colls) {:name project-name})))

(defn find-project-by-name [{db-conn :db} project-name]
  (mc/find-one-as-map db-conn (:projects colls) {:name project-name}))

(defn is-authorized?
  ([{users :users :as project} username action]
   (if-let [{role :role} (first (filter #(= username (:username %)) users))]
     (check-project-role action role)))
  ([{db-conn :db :as db} project-name username action]
   (let [project (find-project-by-name db project-name)]
     (is-authorized? project username action))))

(s/defn new-project :- project-schema
  [{db-conn :db :as db} creator project-name description & [users]]
  {:pre [(every? #(is-user? db {:username %}) (map :username users))
         (not (is-project? db project-name))]}
  (-> (mc/insert-and-return
       db-conn (:projects colls)
       {:name project-name
        :description description
        :created (System/currentTimeMillis)
        :users (map #(select-keys % [:username :role]) users)})
      (dissoc :_id)))

(s/defn update-project
  [{db-conn :db} username project-name update-payload]
  (let [{:keys [creator users updates] :as payload}
        (mc/find-and-modify db-conn (:projects colls)
                            {:name project-name}
                            {$push {:updates update-payload}}
                            {:return-new true})]
    (dissoc payload :_id)))

(defn get-projects
  "retrieves data of projects in which `username` is involved"
  [{db-conn :db} username]
  (->> (mc/find-maps
        db-conn (:projects colls)
        {$or [{:creator username} {"users.username" username}]})
       (map #(dissoc % :_id))))
