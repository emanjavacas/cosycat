(ns cleebo.test.projects-test
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [monger.collection :as mc]
            [monger.operators :refer :all]
            [cleebo.db.projects :as proj]
            [cleebo.db.users :as users]
            [cleebo.test.test-config :refer [db-fixture db]]))

(def project-name "_test_project")
(def creator "user")
(def random-users
  [{:username "hello" :role "guest"}
   {:username "howdy" :role "user"}
   {:username "whatssup" :role "project-lead"}])

(def check-roles
  (conj random-users {:username creator :role "creator"}))

(def boilerplate-user
  {:password "pass"
   :firstname "FOO"
   :lastname "USER"
   :email "foo@bar.com"})

(defn insert-users []
  (doseq [{:keys [username]} check-roles]
    (users/new-user db (assoc boilerplate-user :username username :email (str (rand-int 100000))))))

(defn remove-users []
  (doseq [{:keys [username]} check-roles]
    (users/remove-user db username)))

(defn create-project []
  (proj/new-project db creator project-name "A random project description" random-users))

(defn project-fixture [f]
  (insert-users)
  (create-project)
  (f)
  (proj/erase-project db project-name (mapv :username check-roles))
  (remove-users))

(use-fixtures :once db-fixture project-fixture)

(deftest project-test
  (let [projects (proj/get-projects db creator)]
    (testing "retrieves project"
      (is (not (empty? projects))))
    (testing "user roles are alright"
      (let [{users :users} (proj/get-project db creator project-name)]
        (is (= (apply hash-set users) (apply hash-set check-roles)))))
    (testing "users not in project can't see project"
      (is (= (try (proj/get-project db "a random name!" project-name)
                  (catch clojure.lang.ExceptionInfo e
                    (:message (ex-data e))))
             :user-not-in-project)))))

(deftest remove-project-test
  (testing "user remove rights"
    (is (= (-> (try (proj/remove-project db "hello" project-name)
                    (catch clojure.lang.ExceptionInfo e
                      (ex-data e)))
               :message)
           :not-authorized)))
  (testing "remove-project returns `nil` if not all users agree yet"
    (let [res (proj/remove-project db "howdy" project-name)]
      (is (nil? res))))
  (testing "all agree to remove, remove-project returns `true`"
    (let [_ (proj/remove-project db "whatssup" project-name)
          res (proj/remove-project db creator project-name)]
      (is res)))
  (testing "actually removes"
    (let [user-projects (proj/get-projects db creator)]
      (is (empty? user-projects)))))
