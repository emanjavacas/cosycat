(ns cleebo.test.db
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.test.check.generators :as check-generators]
            [monger.collection :as mc]
            [monger.operators :refer :all]
            [com.stuartsierra.component :as component]
            [schema.core :as s]
            [schema-generators.generators :as g]
            [cleebo.schemas.annotation-schemas :refer [annotation-schema]]
            [cleebo.components.db :refer [new-db colls clear-dbs]]
            [cleebo.db.users :refer [new-user is-user? lookup-user remove-user]]
            [cleebo.db.annotations :as anns]
            [environ.core :refer [env]]))

(defonce db (component/start (new-db (:database-url env))))
;; (do (clear-dbs db))
(def force-int ((g/fmap inc) check-generators/int))

(defn create-dummy-annotation [username & [n]]
  (let [anns (map (fn [m] (assoc m :username username))
                  (g/sample (+ 5 (or n 0)) annotation-schema {s/Int force-int}))]
    (if n
      (vec (take n anns))
      (peek (vec anns)))))

(def payload
  {:hit-id  [5377 9569],
   :ann-map [{:ann      {:key "a", :value "a"},
              :username "user",
              :project  "user-playground",
              :span     {:type "token", :scope 166}, :timestamp 1461920859355}
             {:ann {:key "a", :value "a"},
              :username "user",
              :project "user-playground",
              :span {:type "token", :scope 297},
              :timestamp 1461920859357}]})

(def token-ann
  {:ann {:key "a", :value "a"},
   :username "user",
   :project  "user-playground",
   :span     {:type "token", :scope 166},
   :timestamp 1461920859355})

(def IOB-ann
  {:ann {:key "a", :value "a"},
   :username "user",
   :project  "user-playground",
   :span     {:type "IOB", :scope {:B 160 :O 165}}
   :timestamp 1461920859355})

(def overlapping-IOB-IOB-ann
  {:ann {:key "a", :value "a"},
   :username "user",
   :project  "user-playground",
   :span     {:type "IOB", :scope {:B 159 :O 162}}
   :timestamp 1461920859355})

(def overlapping-IOB-token-ann
  {:ann {:key "a", :value "a"},
   :username "user",
   :project  "user-playground",
   :span     {:type "IOB", :scope {:B 164 :O 168}}
   :timestamp 1461920859355})

(def overlapping-token-IOB-ann
  {:ann {:key "a", :value "a"},
   :username "user",
   :project  "user-playground",
   :span     {:type "token", :scope 164},
   :timestamp 1461920859355})

(deftest annotations-db-test
  (testing "insert token annotation"
    (let [ann-out (anns/new-token-annotation db token-ann)]
      (is (nil? (s/check annotation-schema ann-out)))))
  (testing "find token annotation"
    (is (let [{ann-id :ann-id} (anns/find-ann-id db token-ann)
              retrieved-ann (anns/find-ann-by-id db ann-id)]
          (nil? (s/check annotation-schema retrieved-ann)))))
  (testing "insert IOB annotation"
    (let [ann-out (anns/new-token-annotation db IOB-ann)]
      (is (nil? (s/check annotation-schema ann-out)))))
  (testing "find IOB annotation"
    (is (let [{ann-id :ann-id} (anns/find-ann-id db IOB-ann)
              retrieved-ann (anns/find-ann-by-id db ann-id)]
          (nil? (s/check annotation-schema retrieved-ann)))))
  (testing "attempt IOB overlapping IOB annotation"
    (is (= (-> (try (anns/new-token-annotation db overlapping-IOB-IOB-ann)
                    (catch clojure.lang.ExceptionInfo e
                      (ex-data e)))
               (select-keys [:old-scope :new-scope]))
           {:old-scope (get-in IOB-ann [:span :scope])
            :new-scope (get-in overlapping-IOB-IOB-ann [:span :scope])})))
  (testing "attempt token overlapping IOB annotation"
    (is (= (-> (try (anns/new-token-annotation db overlapping-token-IOB-ann)
                    (catch clojure.lang.ExceptionInfo e
                      (ex-data e)))
               (select-keys [:scope]))
           {:scope (get-in IOB-ann [:span :scope])})))
  (testing "attempt IOB overlapping token annotation"
    (is (= (-> (try (anns/new-token-annotation db overlapping-IOB-token-ann)
                    (catch clojure.lang.ExceptionInfo e
                      (ex-data e)))
               (select-keys [:span :scope]))
           {:scope (get-in token-ann [:span :scope])}))))

(def sample-user
  {:username "foo-user" :password "pass"
   :firstname "FOO" :lastname "USER" :email "foo@bar.com"})

(deftest users-db-test
  (let [new-user-out (new-user db sample-user)
        new-user-exisiting-out (new-user db sample-user)
        is-user?-out (is-user? db sample-user)
        lookup-user-out (lookup-user db {:username "foo@bar.com" :password "pass"})
        remove-user-existing-out (remove-user db "foo-user")
        remove-user-out (remove-user db "foo-user")
        new-user-roles-out (new-user db sample-user :roles ["admin"])
        _ (remove-user db "foo-user")
        new-user-multiple-roles-out (new-user db sample-user :roles ["admin" "user"])
        _ (remove-user db "foo-user")]
    (testing "adding new user"
      (is (= (select-keys new-user-out [:username :roles])
             {:username "foo-user" :roles #{"user"}})))
    (testing "adding existin user"
      (is (= new-user-exisiting-out
             nil)))
    (testing "existing user"
      (is (= is-user?-out
             true)))
    (testing "user lookup"
      (is (= (select-keys lookup-user-out [:username :roles])
             {:username "foo-user" :roles #{"user"}})))
    (testing "remove existing user"
      (is (not (nil? remove-user-existing-out))))
    (testing "remove non-existing user"
      (is (nil? remove-user-out)))
    (testing "user with admin role"
      (is (= (select-keys new-user-roles-out [:username :roles])
             {:username "foo-user" :roles #{"admin"}})))
    (testing "user with multiple roles"
      (is (= (select-keys new-user-multiple-roles-out [:username :roles])
             {:username "foo-user" :roles #{"admin" "user"}})))))
