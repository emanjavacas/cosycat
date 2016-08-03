(ns cleebo.test.vcs-test
  (:refer-clojure :exclude [sort find update remove])
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [monger.collection :as mc]
            [monger.query :refer :all]
            [monger.operators :refer :all]
            [cleebo.vcs :as vcs]
            [cleebo.test.test-config :refer [db-fixture db]]
            [config.core :refer [env]]
            [com.stuartsierra.component :as component]
            [cleebo.components.db :refer [new-db colls clear-dbs]]))

(def coll "test")
(def data {:id 1234567890 :ann {:key "animacy" :value "true"}
           :username "user" :timestamp 12324567890
           :span {:type "IOB" :scope {:B 0 :O 3}}
           :project "project"})

(defn vcs-fixture [f]
  (f)
  (do (mc/drop (:db db) coll) (vcs/drop-vcs (:db db))))

(use-fixtures :once db-fixture vcs-fixture)

(deftest insert-test
  (let [{version :_version id :_id :as doc} (vcs/insert-and-return (:db db) coll data)]
    (testing "insert doesn't modify data except for _id and _version fields"
      (is (= data (dissoc doc :_version :_id))))
    (testing "first version is 0"
      (is (zero? version)))))

(deftest update-test
  (let [{version :_version id :_id :as doc} (vcs/insert-and-return (:db db) coll data)
        _ (vcs/update (:db db) coll version {:_id id} {$set {:randomField 0}})
        _ (vcs/update (:db db) coll (inc version) {:_id id} {$inc {:randomField 1}})
        doc (vcs/find-and-modify (:db db) coll (inc (inc version))
                                 {:_id id} {$set {:value "false"}} {:return-new true})
        {history :history} (vcs/with-history (:db db) doc)]
    (testing "version should be 3"
      (is (= 3 (:_version doc))))
    (testing "history should be sorted"
      (is (= (map :_version history) (clojure.core/sort > (map :_version history)))))
    (testing "there should be one version per update"
      (is (= 3 (count history))))))

(deftest remove-test
  (let [{version :_version id :_id :as doc} (vcs/insert-and-return (:db db) coll data)
        _ (vcs/update (:db db) coll version {:_id id} {$set {:randomField 0}})        
        _ (vcs/remove-by-id (:db db) coll (inc version) id)
        {history :history} (vcs/with-history (:db db) doc)
        try-to-find (mc/find-one-as-map (:db db) coll {:_id id})]
    (testing "actually removes"
      (is (empty? try-to-find)))
    (testing "history is preserved"
      (is (= (range (inc (apply max (map :_version history)))) (clojure.core/sort < (map :_version history)))))
    (testing "last history item"
      (is (:_remove (first history))))))

(deftest upsert-test
  (let [doc (-> data (assoc-in [:ann :key] "inanimacy"))
        version 0]
    (testing "upsert not allowed"
        (try (vcs/update (:db db) coll version {:_id "asd"} {$set {:random "Random"}} {:upsert true})
             (catch clojure.lang.ExceptionInfo e
               (is (= :upsert-not-allowed (:message (ex-data e)))))))
    (testing "multi not allowed"
        (try (vcs/update (:db db) coll version {:_id "asd"} {$set {:random "Random"}} {:multi true})
             (catch clojure.lang.ExceptionInfo e
               (is (= :multi-not-allowed (:message (ex-data e)))))))))

(deftest drop-test
  (vcs/with-hist-coll "_test_coll"
    (let [ids (vec (for [_ (range 10)] (-> (vcs/insert-and-return (:db db) coll data) :_id)))]
      (vcs/drop (:db db) coll)
      (let [removed-docs (mc/find-maps (:db db) coll {})]
        (testing "all are removed"
          (is (empty? removed-docs)))
        (testing "vcs docs are still there with :_remove set to true"
          (is (every? :_remove (mc/find-maps (:db db) "_test_coll" {:docId ids}))))))))

