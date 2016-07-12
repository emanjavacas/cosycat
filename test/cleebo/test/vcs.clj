(ns cleebo.test.vcs
  (:refer-clojure :exclude [sort find update remove])
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [monger.collection :as mc]
            [monger.query :refer :all]
            [monger.operators :refer :all]
            [cleebo.vcs :as vcs]
            [config.core :refer [env]]
            [com.stuartsierra.component :as component]
            [cleebo.components.db :refer [new-db colls clear-dbs]]))

(def coll "test")
(def data {:id 1234567890 :ann {:key "animacy" :value "true"}
           :username "user" :timestamp 12324567890
           :span {:type "IOB" :scope {:B 0 :O 3}}
           :project "project"})

(defn db-fixture [f]
  (def db (component/start (new-db (:database-url env))))
  (f)
  (do (mc/drop (:db db) coll) (vcs/drop-vcs (:db db)) (component/stop db)))

(use-fixtures :once db-fixture)

(deftest insert-test
  (let [{version :_version id :_id :as doc} (vcs/insert-and-return (:db db) coll data)]
    (testing "insert doesn't modify data except for _id and _version fields"
      (is (= data (dissoc doc :_version :_id))))
    (testing "first version is 0"
      (is (zero? version)))))

(deftest update-test
  (let [{version :_version id :_id :as doc} (vcs/insert-and-return (:db db) coll data)
        _ (vcs/update (:db db) coll {:_id id} {$set {:randomField 0}})
        _ (vcs/update (:db db) coll {:_id id} {$inc {:randomField 1}})
        doc (vcs/find-and-modify (:db db) coll {:_id id} {$set {:value "false"}} {:return-new true})
        {history :history} (vcs/with-history (:db db) doc)]
    (testing "version should be 3"
      (is (= 3 (:_version doc))))
    (testing "history should be sorted"
      (is (= (map :_version history) (clojure.core/sort > (map :_version history)))))
    (testing "there should be one version per update"
      (is (= 3 (count history))))))

(deftest upsert-test
  (let [doc (-> data (assoc-in [:ann :key] "inanimacy"))]
    (testing "upsert not allowed"
        (try (vcs/update (:db db) coll {:_id "asd"} {:upsert true})
             (catch clojure.lang.ExceptionInfo e
               (is (= :upsert-not-allowed (:reason (ex-data e)))))))
    (testing "multi not allowed"
        (try (vcs/update (:db db) coll {:_id "asd"} {:multi true})
             (catch clojure.lang.ExceptionInfo e
               (is (= :multi-not-allowed (:reason (ex-data e)))))))))
