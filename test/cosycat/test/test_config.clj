(ns cosycat.test.test-config
  (:require [com.stuartsierra.component :as component]
            [monger.collection :as mc]
            [cosycat.vcs :as vcs]
            [cosycat.components.db :refer [new-db]]
            [cosycat.db.users :as users]
            [cosycat.db.projects :as proj]
            [cosycat.db.annotations :as anns]            
            [cosycat.avatar :refer [new-avatar]]
            [cosycat.db-utils :refer [clear-dbs]]
            [cosycat.app-utils :refer [server-project-name]]
            [config.core :refer [env]]))

(def test-db "mongodb://127.0.0.1:27017/cosycatTest")

(defn db-fixture [f]
  (def db (component/start (new-db test-db)))
  (clear-dbs db)
  (f)
  (clear-dbs db)
  (component/stop db))

(def project-data
  {:project-name "test_project"
   :creator "user"
   :random-users [{:username "hello" :role "guest"}
                  {:username "guest" :role "guest"}
                  {:username "howdy" :role "user"}
                  {:username "whatssup" :role "project-lead"}]
   :user-roles [{:username "hello" :role "guest"}
                {:username "guest" :role "guest"}
                {:username "howdy" :role "user"}
                {:username "whatssup" :role "project-lead"}
                {:username "user" :role "creator"}]
   :boilerplate-user {:password "pass"
                      :firstname "FOO"
                      :lastname "USER"
                      :email "foo@bar.com"}
   :token-ann {:ann {:key "a", :value "a"},
               :username "howdy",
               :span     {:type "token", :scope 166},
               :corpus "my-corpus"
               :hit-id "0.1.2"
               :query "my-query"
               :timestamp 1461920859355}})

(defn insert-users []
  (let [{:keys [user-roles boilerplate-user]} project-data]
    (doseq [{:keys [username]} user-roles]
      (println (format "inserting [%s]" username))
      (users/new-user db (assoc boilerplate-user :username username :email (str (rand-int 100000)))))))

(defn remove-users []
  (let [{:keys [user-roles]} project-data]
    (doseq [{:keys [username]} user-roles]
      (users/remove-user db username))))

(defn create-project []
  (let [{:keys [creator project-name random-users]} project-data]
    (proj/new-project db creator project-name "A random project description" random-users)))

(defn insert-some-annotations []
  (let [{:keys [project-name token-ann]} project-data
        ann (anns/insert-annotation db project-name token-ann)]
    (anns/update-annotation
     db project-name
     (merge (select-keys ann [:username :query :_version :_id :corpus :hit-id])
            {:value "randomnewvalue" :timestamp 12039102}))))

(defn project-fixture [f]
  (let [{:keys [project-name user-roles]} project-data]
    (insert-users)
    (create-project)
    (insert-some-annotations)
    (f)
    (proj/erase-project db project-name (mapv :username user-roles))
    (mc/drop (:db db) (server-project-name project-name))
    (remove-users)))
