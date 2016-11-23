(ns cosycat.test.projects-test
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [monger.collection :as mc]
            [monger.operators :refer :all]
            [cosycat.vcs :as vcs]
            [cosycat.app-utils :refer [server-project-name]]
            [cosycat.db.projects :as proj]
            [cosycat.db.users :as users]
            [cosycat.test.test-config :refer [db-fixture project-fixture project-data db]]))

(use-fixtures :once db-fixture project-fixture)

(deftest project-test
  (let [{:keys [creator project-name user-roles]} project-data
        projects (proj/get-projects db "hello")
        removed? (fn [] (let [projects (proj/get-projects db creator)]
                          (and
                           (empty? projects)
                           (empty? (mc/find-maps (:db db) (server-project-name project-name) {})))))]
    (testing "retrieves project"
      (is (not (empty? projects))))
    (testing "user roles are alright"
      (let [{users :users :as project} (proj/get-project db creator project-name)]
        (is (= (apply hash-set users) (apply hash-set user-roles)))))
    (testing "users outside in project can't see project"
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
    (testing "remove-project adds username to agreed in delete-project-agree issue"
      (let [_ (proj/remove-project db "howdy" project-name)
            {{agreed :agreed} :data :as issue} (proj/find-delete-project-issue db project-name)]
        (is (not (nil? issue)))
        (is (some #{"howdy"} agreed))))
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

