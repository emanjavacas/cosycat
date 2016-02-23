(ns cleebo.test.db-users-test
  (:require [clojure.test :refer [deftest testing is]]
            [com.stuartsierra.component :as component]
            [cleebo.db.users :refer [new-user is-user? lookup-user remove-user]]
            [cleebo.db.roles :refer [app-roles]]
            [cleebo.db.component :refer [new-db]]))

(def db (component/start (new-db {:url "mongodb://127.0.0.1:27017/cleeboTest"})))

(deftest users-db-test
  (let [new-user-out (new-user db {:username "foo-user" :password "pass"})
        new-user-exisiting-out (new-user db {:username "foo-user" :password "pass"})
        is-user?-out (is-user? db {:username "foo-user" :password "pass"})
        lookup-user-out (lookup-user db "foo-user" "pass")
        remove-user-existing-out (remove-user db "foo-user")
        remove-user-out (remove-user db "foo-user")
        new-user-roles-out (new-user db {:username "foo-user" :password "pass"} :roles [:admin])
        _ (remove-user db "foo-user")
        new-user-multiple-roles-out (new-user db {:username "foo-user" :password "pass"}
                                              :roles [:admin :user])
        _ (remove-user db "foo-user")]
    (testing "adding new user"
      (is (= new-user-out
             {:username "foo-user" :roles #{:cleebo.db.roles/user}})))
    (testing "adding existin user"
      (is (= new-user-exisiting-out
             nil)))
    (testing "existing user"
      (is (= is-user?-out
             true)))
    (testing "user lookup"
      (is (= lookup-user-out
             {:username "foo-user" :roles #{:cleebo.db.roles/user}})))
    (testing "remove existing user"
      (is (not (nil? remove-user-existing-out))))
    (testing "remove non-existing user"
      (is (nil? remove-user-out)))
    (testing "user with admin role"
      (is (= new-user-roles-out
             {:username "foo-user" :roles #{:cleebo.db.roles/admin}})))
    (testing "user with multiple roles"
      (is (= new-user-multiple-roles-out
             {:username "foo-user" :roles #{:cleebo.db.roles/admin :cleebo.db.roles/user}})))))

