(ns cosycat.test.projects-test
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [monger.collection :as mc]
            [monger.operators :refer :all]
            [cosycat.vcs :as vcs]
            [cosycat.app-utils :refer [server-project-name]]
            [cosycat.db.projects :as proj]
            [cosycat.db.users :as users]
            [cosycat.db.annotations :as anns]
            [cosycat.test.test-config :refer [db-fixture db]]))

(def project-name "test_project")
(def creator "user")
(def random-users
  [{:username "hello" :role "guest"}
   {:username "guest" :role "guest"}
   {:username "howdy" :role "user"}
   {:username "whatssup" :role "project-lead"}])

(def token-ann
  {:ann {:key "a", :value "a"},
   :username "howdy",
   :span     {:type "token", :scope 166},
   :corpus "my-corpus"
   :hit-id "0.1.2"
   :query "my-query"
   :timestamp 1461920859355})

(def check-roles
  (conj random-users {:username creator :role "creator"}))

(def boilerplate-user
  {:password "pass"
   :firstname "FOO"
   :lastname "USER"
   :email "foo@bar.com"})

(defn insert-users []
  (doseq [{:keys [username]} check-roles]
    (println (format "inserting [%s]" username))
    (users/new-user db (assoc boilerplate-user :username username :email (str (rand-int 100000))))))

(defn remove-users []
  (doseq [{:keys [username]} check-roles]
    (users/remove-user db username)))

(defn create-project []
  (proj/new-project db creator project-name "A random project description" random-users))

(defn insert-some-annotations []
  (let [ann (anns/insert-annotation db project-name token-ann)]
    (anns/update-annotation
     db project-name
     (merge (select-keys ann [:username :query :_version :_id :corpus :hit-id])
            {:value "randomnewvalue" :timestamp 12039102}))))

(defn project-fixture [f]
  (insert-users)
  (create-project)
  (insert-some-annotations)
  (f)
  (proj/erase-project db project-name (mapv :username check-roles))
  (mc/drop (:db db) (server-project-name project-name))
  (remove-users))

(use-fixtures :once db-fixture project-fixture)

(deftest project-test
  (let [projects (proj/get-projects db "hello")
        removed? (fn [] (let [projects (proj/get-projects db creator)]
                          (and
                           (empty? projects)
                           (empty? (mc/find-maps (:db db) (server-project-name project-name) {})))))]
    (testing "retrieves project"
      (is (not (empty? projects))))
    (testing "user roles are alright"
      (let [{users :users :as project} (proj/get-project db creator project-name)]
        (is (= (apply hash-set users) (apply hash-set check-roles)))))
    (testing "users not in project can't see project"
      (is (= (try (proj/get-project db "a random name!" project-name)
                  (catch clojure.lang.ExceptionInfo e
                    (:code (ex-data e))))
             :user-not-in-project)))
    (testing "authorized user new role"
      (let [{:keys [username role]} (proj/update-user-role db creator project-name "hello" "user")]
        (is (= role "user"))))
    (testing "unauthorized update"
      (is (= (try (proj/update-user-role db "hello" project-name creator "guest")
                  (catch clojure.lang.ExceptionInfo e
                    (-> e ex-data :code)))
             :not-authorized)))
    (testing "user remove rights"
      (is (= (try (proj/remove-project db "guest" project-name)
                  (catch clojure.lang.ExceptionInfo e
                    (-> e ex-data :code)))
             :not-authorized)))
    (testing "remove-project adds username to updates type delete-project-agree"
      (let [_ (proj/remove-project db "howdy" project-name)
            {:keys [issues] :as project} (proj/get-project db "howdy" project-name)]
        (is (not (empty? issues)))
        (is (some #{"howdy"} (->> issues
                                  (filter #(= "delete-project-agree" (:type %)))
                                  (map :username))))))
    (testing "project is not yet removed"
      (is (not (removed?))))
    (testing "all agree to remove, remove-project returns nil"
      (let [_ (proj/remove-project db "whatssup" project-name)
            _ (proj/remove-project db "hello" project-name)
            res (proj/remove-project db creator project-name)]
        (is (nil? res))))
    (testing "actually removes; annotations are also removed"
      (is (removed?)))
    (testing "annotation history is still in vcs collection with field _remove"
      (let [hist (mc/find-maps (:db db) vcs/*hist-coll-name* {})]
        (is (not (empty? hist)))
        (is (every? :_remove hist))))))

